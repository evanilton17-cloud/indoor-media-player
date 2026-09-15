import mongoose from 'mongoose';

const contentSchema = new mongoose.Schema(
  {
    ownerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', index: true },
    name: { type: String, required: true },
    type: { type: String, enum: ['VIDEO', 'IMAGE', 'AUDIO'], required: true },
    storageUrl: { type: String, required: true },
    storagePath: String,
    sizeBytes: { type: Number, default: 0 },
    durationSeconds: { type: Number, default: 0 },
    thumbnailUrl: String,
    tags: [String],
    version: { type: Number, default: 1 }
  },
  { timestamps: true }
);

export default mongoose.model('Content', contentSchema);