import amqp from 'amqplib';
import express from 'express';
import admin from 'firebase-admin';
import dotenv from 'dotenv';
import nodemailer from 'nodemailer';

dotenv.config();

const PORT = Number(process.env.PORT || 8086);
const exchangeName = process.env.RABBITMQ_EXCHANGE || 'vendora.events';
const queueName = process.env.RABBITMQ_NOTIFICATION_QUEUE || 'vendora.notifications';
const maxRetries = Number(process.env.NOTIFICATION_MAX_RETRIES || 3);
const retryBaseDelayMs = Number(process.env.NOTIFICATION_RETRY_BASE_DELAY_MS || 1000);
const eventKeys = ['order.placed', 'order.shipped', 'chat.offline_message', 'user.reviewed'];
const rabbitMqUrl = process.env.RABBITMQ_URL || [
    'amqp://',
    encodeURIComponent(process.env.RABBITMQ_USER || 'vendora'),
    ':',
    encodeURIComponent(process.env.RABBITMQ_PASS || 'pass'),
    '@',
    process.env.RABBITMQ_HOST || 'localhost',
    ':',
    process.env.RABBITMQ_PORT || '5672',
].join('');

const smtpConfigured = Boolean(process.env.MAIL_HOST && process.env.MAIL_FROM);
const mailer = smtpConfigured
    ? nodemailer.createTransport({
        host: process.env.MAIL_HOST,
        port: Number(process.env.MAIL_PORT || 587),
        secure: process.env.MAIL_SECURE === 'true',
        auth: process.env.MAIL_USERNAME
            ? { user: process.env.MAIL_USERNAME, pass: process.env.MAIL_PASSWORD || '' }
            : undefined,
    })
    : null;

let firebaseReady = false;
if (process.env.FIREBASE_PROJECT_ID && process.env.FIREBASE_CLIENT_EMAIL && process.env.FIREBASE_PRIVATE_KEY) {
    admin.initializeApp({
        credential: admin.credential.cert({
            projectId: process.env.FIREBASE_PROJECT_ID,
            clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
            privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
        }),
    });
    firebaseReady = true;
}

const app = express();
let rabbitChannel;

app.get('/', (request, response) => response.json({
    service: 'notification',
    status: 'ok',
    rabbitmq: Boolean(rabbitChannel),
    smtp: smtpConfigured,
    firebase: firebaseReady,
}));

const firstValue = (payload, keys) => keys.map((key) => payload[key]).find(Boolean);

const eventDetails = (routingKey, payload) => {
    const defaults = {
        'order.placed': {
            subject: `Order ${payload.order_id || payload.orderId || ''} placed`,
            text: 'Your Vendora order has been placed successfully.',
            recipients: ['buyer_email', 'buyerEmail', 'email'],
        },
        'order.shipped': {
            subject: `Order ${payload.order_id || payload.orderId || ''} shipped`,
            text: 'Your Vendora order has shipped.',
            recipients: ['buyer_email', 'buyerEmail', 'email'],
        },
        'chat.offline_message': {
            subject: 'You have a new Vendora message',
            text: payload.preview || payload.message || 'You received a new message on Vendora.',
            recipients: ['recipient_email', 'recipientEmail', 'email'],
        },
        'user.reviewed': {
            subject: 'Your Vendora product received a review',
            text: payload.review_text || payload.reviewText || 'A customer reviewed your product.',
            recipients: ['vendor_email', 'vendorEmail', 'email'],
        },
    };
    const details = defaults[routingKey];
    return {
        subject: payload.subject || details.subject,
        text: payload.text || details.text,
        html: payload.html || `<p>${payload.text || details.text}</p>`,
        to: payload.to || firstValue(payload, details.recipients),
        token: firstValue(payload, ['device_token', 'deviceToken', 'fcm_token', 'fcmToken']),
    };
};

const sendNotification = async (routingKey, payload) => {
    const details = eventDetails(routingKey, payload);
    const tasks = [];

    if (details.to && mailer) {
        tasks.push(mailer.sendMail({
            from: process.env.MAIL_FROM,
            to: details.to,
            subject: details.subject,
            text: details.text,
            html: details.html,
        }));
    } else if (details.to && !mailer) {
        console.warn(`SMTP is not configured; email skipped for ${routingKey}`);
    }

    if (details.token && firebaseReady) {
        tasks.push(admin.messaging().send({
            token: details.token,
            notification: { title: details.subject, body: details.text },
        }));
    }

    if (tasks.length === 0) console.warn(`No notification destination found for ${routingKey}`);
    await Promise.all(tasks);
};

const setupRabbitMq = async () => {
    const connection = await amqp.connect(rabbitMqUrl);
    rabbitChannel = await connection.createChannel();
    await rabbitChannel.assertExchange(exchangeName, 'topic', { durable: true });
    await rabbitChannel.assertQueue(queueName, { durable: true });

    for (const routingKey of eventKeys) {
        await rabbitChannel.bindQueue(queueName, exchangeName, routingKey);
    }
    for (let retry = 1; retry <= maxRetries; retry += 1) {
        await rabbitChannel.assertQueue(`${queueName}.retry.${retry}`, {
            durable: true,
            arguments: {
                'x-message-ttl': retryBaseDelayMs * (2 ** (retry - 1)),
                'x-dead-letter-exchange': exchangeName,
            },
        });
    }

    await rabbitChannel.consume(queueName, async (message) => {
        if (!message) return;
        const routingKey = message.fields.routingKey;
        const retry = Number(message.properties.headers?.['x-retry-count'] || 0);
        try {
            await sendNotification(routingKey, JSON.parse(message.content.toString()));
            rabbitChannel.ack(message);
            console.log(`Processed notification event ${routingKey}`);
        } catch (error) {
            if (retry < maxRetries) {
                rabbitChannel.sendToQueue(`${queueName}.retry.${retry + 1}`, message.content, {
                    persistent: true,
                    contentType: message.properties.contentType || 'application/json',
                    headers: { 'x-retry-count': retry + 1 },
                });
                rabbitChannel.ack(message);
                console.error(`Notification ${routingKey} failed; scheduled retry ${retry + 1}: ${error.message}`);
            } else {
                rabbitChannel.nack(message, false, false);
                console.error(`Notification ${routingKey} discarded after ${maxRetries} retries: ${error.message}`);
            }
        }
    }, { noAck: false });

    connection.on('close', () => {
        rabbitChannel = undefined;
        console.error('RabbitMQ connection closed');
    });
    connection.on('error', (error) => console.error(`RabbitMQ connection error: ${error.message}`));
};

app.listen(PORT, () => console.log(`Notification service listening on port ${PORT}`));

const connectWithRetry = async () => {
    try {
        await setupRabbitMq();
        console.log(`Notification consumer listening on ${queueName}`);
    } catch (error) {
        console.error(`RabbitMQ connection failed: ${error.message}`);
        setTimeout(connectWithRetry, 5000);
    }
};

connectWithRetry();