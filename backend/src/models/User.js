import mongoose from 'mongoose';

const userSchema = new mongoose.Schema(
  {
    name: { type: String, required: true },
    email: { type: String, required: true, unique: true, lowercase: true },
    password: { type: String, required: true },
    role: {
      type: String,
      enum: ['owner', 'admin', 'viewer'],
      default: 'owner'
    },
    plan: {
      type: String,
      enum: ['free', 'starter', 'pro', 'enterprise'],
      default: 'free'
    },
    planScreens: { type: Number, default: 1 },
    stripeCustomerId: String,
    stripeSubscriptionId: String,
    whiteLabel: {
      enabled: { type: Boolean, default: false },
      brandName: String,
      logoUrl: String,
      primaryColor: String
    },
    active: { type: Boolean, default: true }
  },
  { timestamps: true }
);

export default mongoose.model('User', userSchema);