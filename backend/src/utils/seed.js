import bcrypt from 'bcryptjs';
import mongoose from 'mongoose';
import dotenv from 'dotenv';
import User from '../models/User.js';
import Device from '../models/Device.js';
import Content from '../models/Content.js';
import Playlist from '../models/Playlist.js';
import Schedule from '../models/Schedule.js';
import { connectDatabase } from '../db.js';

dotenv.config();

await connectDatabase();
console.log('Banco de dados pronto');

const user = await User.findOne({ email: 'admin@test.com' });
if (user) {
  console.log('Seed já executado');
  process.exit(0);
}

const hash = await bcrypt.hash('123456', 10);
const createdUser = await User.create({
  name: 'Admin',
  email: 'admin@test.com',
  password: hash,
  plan: 'free',
  planScreens: 1
});
console.log('Usuário criado:', createdUser.email);

const content1 = await Content.create({
  ownerId: createdUser._id,
  name: 'video-promo.mp4',
  type: 'VIDEO',
  storageUrl: 'https://example.com/sample-video.mp4',
  durationSeconds: 30
});

const content2 = await Content.create({
  ownerId: createdUser._id,
  name: 'banner-academia.jpg',
  type: 'IMAGE',
  storageUrl: 'https://example.com/sample-image.jpg',
  durationSeconds: 5
});

const playlist = await Playlist.create({
  ownerId: createdUser._id,
  name: 'Playlist Principal',
  items: [
    { contentId: content1._id, name: content1.name, type: content1.type, durationSeconds: 30, order: 0 },
    { contentId: content2._id, name: content2.name, type: content2.type, durationSeconds: 5, order: 1 }
  ]
});

const device = await Device.create({
  ownerId: createdUser._id,
  paired: true,
  uniqueId: 'TEST-DEVICE-001',
  name: 'TV Teste',
  model: 'Android TV Box X1',
  status: { online: false, itemCount: 2 }
});

await Schedule.create({
  ownerId: createdUser._id,
  name: 'Horário Comercial',
  playlistId: playlist._id,
  deviceIds: [device._id],
  rules: { daysOfWeek: [1,2,3,4,5], startTime: '08:00', endTime: '18:00' },
  priority: 10,
  active: true
});

console.log('✅ Seed executado com sucesso');
console.log('Login: admin@test.com / 123456');
console.log('Device ID para teste:', device.uniqueId);
process.exit(0);