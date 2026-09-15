import mongoose from 'mongoose';

const proofOfPlaySchema = new mongoose.Schema(
  {
    ownerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', index: true },
    deviceId: { type: mongoose.Schema.Types.ObjectId, ref: 'Device', index: true },
    contentId: { type: mongoose.Schema.Types.ObjectId, ref: 'Content', index: true },
    contentName: String,
    type: String,
    startedAt: Date,
    endedAt: Date,
    durationSeconds: Number,
    reference: { type: String, unique: true }
  },
  { timestamps: true }
);

proofOfPlaySchema.index({ ownerId: 1, deviceId: 1, startedAt: -1 });

export default mongoose.model('ProofOfPlay', proofOfPlaySchema);