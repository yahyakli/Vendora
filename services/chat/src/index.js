import express from 'express';
import dotenv from 'dotenv';
import mongoose from 'mongoose';
import { createServer } from 'node:http';
import { Server } from 'socket.io';
import jwt from 'jsonwebtoken';
import amqp from 'amqplib';
import { Conversation } from './models/Conversation.js';

dotenv.config();

const app = express();
const httpServer = createServer(app);
const io = new Server(httpServer, {
    cors: {
        origin: process.env.CORS_ORIGIN || '*'
    }
});

app.use(express.json());

app.get('/', (req, res) => {
    res.json({
        message: 'Chat service running'
    });
});

app.get('/health', (req, res) => {
    res.json({ status: 'ok', service: 'chat' });
});

const PORT = process.env.PORT || 8084;
const authServiceUrl = process.env.AUTH_SERVICE_URL || 'http://localhost:8081';
const rabbitMqUrl = process.env.RABBITMQ_URL || [
    'amqp://',
    encodeURIComponent(process.env.RABBITMQ_USER || 'vendora'),
    ':',
    encodeURIComponent(process.env.RABBITMQ_PASS || 'pass'),
    '@',
    process.env.RABBITMQ_HOST || 'localhost',
    ':',
    process.env.RABBITMQ_PORT || '5672'
].join('');
const rabbitExchange = process.env.RABBITMQ_EXCHANGE || 'vendora.events';
let rabbitChannel;
const userRoom = (userId) => `user:${userId}`;
const conversationRoom = (conversationId) => `conversation:${conversationId}`;
const mongoUri = process.env.MONGODB_URI || [
    'mongodb://',
    encodeURIComponent(process.env.MONGO_USER || 'vendora'),
    ':',
    encodeURIComponent(process.env.MONGO_PASS || 'pass'),
    '@',
    process.env.MONGO_HOST || 'localhost',
    ':',
    process.env.MONGO_PORT || '27017',
    '/',
    process.env.MONGO_DATABASE || 'vendora_chat',
    '?authSource=admin'
].join('');

const jwtSecret = process.env.JWT_SECRET;
const jwtKey = jwtSecret ? Buffer.from(jwtSecret, 'base64') : null;

const resolveUserFromToken = async (token) => {
    let claims = {};
    if (jwtKey) {
        try {
            claims = jwt.verify(token, jwtKey);
        } catch {
            throw new Error('Invalid or expired authentication token');
        }
    }

    let userId = Number(claims.user_id || claims.userId || claims.id);
    let roles = [];
    if (!Number.isInteger(userId) || userId <= 0) {
        const response = await fetch(`${authServiceUrl}/auth/validate?token=${encodeURIComponent(token)}`);
        if (!response.ok) {
            throw new Error('Token validation failed');
        }

        const validation = await response.json();
        userId = Number(validation.user_id || validation.userId);
        roles = validation.roles || (validation.role ? [validation.role] : []);
    }

    if (!Number.isInteger(userId) || userId <= 0) {
        throw new Error('Authentication token does not contain a valid user ID');
    }

    return { id: userId, email: claims.sub, roles };
};

const getToken = (socket) => {
    const authToken = socket.handshake.auth?.token;
    if (authToken) {
        return authToken.startsWith('Bearer ') ? authToken.slice(7) : authToken;
    }

    const header = socket.handshake.headers.authorization;
    return header?.startsWith('Bearer ') ? header.slice(7) : null;
};

const getRequestToken = (request) => {
    const header = request.headers.authorization;
    return header?.startsWith('Bearer ') ? header.slice(7) : null;
};

const authenticateRequest = async (request, response, next) => {
    const token = getRequestToken(request);
    if (!token) {
        return response.status(401).json({ message: 'Authentication token is required' });
    }

    try {
        request.user = await resolveUserFromToken(token);
        return next();
    } catch (error) {
        return response.status(401).json({ message: error.message });
    }
};

const isUserConnected = (userId) => Boolean(io.sockets.adapter.rooms.get(userRoom(userId))?.size);

const publishOfflineMessage = async (payload) => {
    if (!rabbitChannel) {
        throw new Error('RabbitMQ is not connected');
    }

    rabbitChannel.publish(
        rabbitExchange,
        'chat.offline_message',
        Buffer.from(JSON.stringify(payload)),
        { contentType: 'application/json', persistent: true }
    );
};

const getConversationForUser = async (conversationId, userId) => {
    if (!conversationId) {
        throw new Error('conversationId is required');
    }

    const conversation = await Conversation.findOne({
        _id: conversationId,
        participantIds: userId
    });

    if (!conversation) {
        throw new Error('Conversation not found or access denied');
    }

    return conversation;
};

app.get('/chat/conversations/:userId', authenticateRequest, async (req, res, next) => {
    const requestedUserId = Number(req.params.userId);
    if (requestedUserId !== req.user.id) {
        return res.status(403).json({ message: 'Access denied' });
    }

    try {
        const conversations = await Conversation.find({ participantIds: requestedUserId })
            .sort({ updatedAt: -1 })
            .lean();
        return res.json(conversations);
    } catch (error) {
        return next(error);
    }
});

app.get('/chat/messages/:conversationId', authenticateRequest, async (req, res, next) => {
    try {
        const conversation = await getConversationForUser(req.params.conversationId, req.user.id);
        return res.json(conversation.messages);
    } catch (error) {
        return next(error);
    }
});

app.post('/chat/conversations', authenticateRequest, async (req, res, next) => {
    const participantIds = [...new Set((req.body.participantIds || []).map(Number))];
    if (participantIds.length < 2 || !participantIds.includes(req.user.id)) {
        return res.status(400).json({
            message: 'participantIds must contain at least two users including the requester'
        });
    }

    try {
        let conversation = await Conversation.findOne({
            participantIds: { $all: participantIds, $size: participantIds.length }
        });
        if (!conversation) {
            conversation = await Conversation.create({ participantIds });
        }

        return res.status(201).json(conversation);
    } catch (error) {
        return next(error);
    }
});

io.use(async (socket, next) => {
    if (!jwtKey) {
        return next(new Error('JWT_SECRET is not configured'));
    }

    const token = getToken(socket);
    if (!token) {
        return next(new Error('Authentication token is required'));
    }

    try {
        const claims = jwt.verify(token, jwtKey);
        let userId = Number(claims.user_id || claims.userId || claims.id);

        if (!Number.isInteger(userId) || userId <= 0) {
            const response = await fetch(`${authServiceUrl}/auth/validate?token=${encodeURIComponent(token)}`);
            if (!response.ok) {
                return next(new Error('Token validation failed'));
            }

            const validation = await response.json();
            userId = Number(validation.user_id || validation.userId);
        }

        socket.user = {
            email: claims.sub,
            id: userId
        };

        if (!Number.isInteger(socket.user.id) || socket.user.id <= 0) {
            return next(new Error('JWT does not contain a valid user ID'));
        }

        return next();
    } catch {
        return next(new Error('Invalid or expired authentication token'));
    }
});

io.on('connection', async (socket) => {
    const { id: userId } = socket.user;
    socket.join(userRoom(userId));

    try {
        const conversations = await Conversation.find({ participantIds: userId }, { _id: 1 });
        conversations.forEach((conversation) => socket.join(conversationRoom(conversation.id)));
    } catch (error) {
        socket.emit('chat:error', { message: 'Unable to load conversation rooms' });
        console.error('Failed to join conversation rooms:', error.message);
    }

    socket.on('message:send', async (payload = {}, acknowledge) => {
        try {
            const conversation = await getConversationForUser(payload.conversationId, userId);
            const content = typeof payload.content === 'string' ? payload.content.trim() : '';
            const recipientId = Number(payload.recipientId);

            if (!content || content.length > 5000) {
                throw new Error('Message content must be between 1 and 5000 characters');
            }
            if (!conversation.participantIds.includes(recipientId) || recipientId === userId) {
                throw new Error('Recipient is not a valid conversation participant');
            }

            conversation.messages.push({ senderId: userId, recipientId, content });
            const message = conversation.messages.at(-1);
            await conversation.save();

            const messagePayload = {
                conversationId: conversation.id,
                message: message.toObject()
            };
            io.to(conversationRoom(conversation.id)).emit('message:received', messagePayload);

            if (!isUserConnected(recipientId)) {
                await publishOfflineMessage({
                    conversationId: conversation.id,
                    recipientId,
                    message: messagePayload.message
                });
            }

            acknowledge?.({ ok: true, message: messagePayload.message });
        } catch (error) {
            acknowledge?.({ ok: false, error: error.message });
        }
    });

    for (const event of ['typing:start', 'typing:stop']) {
        socket.on(event, async (payload = {}, acknowledge) => {
            try {
                const conversation = await getConversationForUser(payload.conversationId, userId);
                socket.to(conversationRoom(conversation.id)).emit(event, {
                    conversationId: conversation.id,
                    userId
                });
                acknowledge?.({ ok: true });
            } catch (error) {
                acknowledge?.({ ok: false, error: error.message });
            }
        });
    }

    socket.on('read:receipt', async (payload = {}, acknowledge) => {
        try {
            const conversation = await getConversationForUser(payload.conversationId, userId);
            const message = conversation.messages.id(payload.messageId);
            if (!message) {
                throw new Error('Message not found');
            }
            if (message.recipientId !== userId) {
                throw new Error('Only the recipient can mark a message as read');
            }

            message.readAt = new Date();
            await conversation.save();
            socket.to(conversationRoom(conversation.id)).emit('read:receipt', {
                conversationId: conversation.id,
                messageId: message.id,
                userId,
                readAt: message.readAt
            });
            acknowledge?.({ ok: true, readAt: message.readAt });
        } catch (error) {
            acknowledge?.({ ok: false, error: error.message });
        }
    });
});

const start = async () => {
    await mongoose.connect(mongoUri);
    const rabbitConnection = await amqp.connect(rabbitMqUrl);
    rabbitChannel = await rabbitConnection.createChannel();
    await rabbitChannel.assertExchange(rabbitExchange, 'topic', { durable: true });

    httpServer.listen(PORT, () => {
        console.log(`Chat service running on port ${PORT}`);
    });
};

start().catch((error) => {
    console.error('Failed to start chat service:', error.message);
    process.exitCode = 1;
});

export { app, io, start };