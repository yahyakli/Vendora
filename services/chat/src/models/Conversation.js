import mongoose from 'mongoose';

const MessageSchema = new mongoose.Schema(
    {
        senderId: {
            type: Number,
            required: true
        },
        recipientId: {
            type: Number,
            required: true
        },
        content: {
            type: String,
            required: true,
            trim: true,
            maxlength: 5000
        },
        sentAt: {
            type: Date,
            default: Date.now
        },
        readAt: {
            type: Date,
            default: null
        }
    },
    { _id: true }
);

const ConversationSchema = new mongoose.Schema(
    {
        participantIds: {
            type: [Number],
            required: true,
            validate: {
                validator: (ids) => ids.length >= 2,
                message: 'A conversation requires at least two participants'
            }
        },
        messages: {
            type: [MessageSchema],
            default: []
        }
    },
    {
        collection: 'conversations',
        timestamps: true
    }
);

ConversationSchema.index({ participantIds: 1 });
ConversationSchema.index({ 'messages.content': 'text' });

export const Conversation = mongoose.model('Conversation', ConversationSchema);
