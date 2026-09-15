import mongoose from 'mongoose';

const playlistSchema = new mongoose.Schema(
  {
    ownerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', index: true },
    name: { type: String, required: true },
    description: String,
    items: [
      {
        contentId: { type: mongoose.Schema.Types.ObjectId, ref: 'Content' },
        name: String,
        type: { type: String, enum: ['VIDEO', 'IMAGE', 'AUDIO'] },
        durationSeconds: { type: Number, default: 0 },
        order: { type: Number, default: 0 }
      }
    ],
    loop: { type: Boolean, default: true }
  },
  { timestamps: true }
);

export default mongoose.model('Playlist', playlistSchema);