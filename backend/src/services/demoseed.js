import bcrypt from 'bcryptjs';
import User from '../models/User.js';
import Device from '../models/Device.js';
import Content from '../models/Content.js';
import Playlist from '../models/Playlist.js';
import Schedule from '../models/Schedule.js';

export async function seedDemoData() {
  const count = await User.countDocuments();
  if (count > 0) return false;

  const hash = await bcrypt.hash('123456', 10);
  const user = await User.create({
    name: 'Admin',
    email: 'admin@test.com',
    password: hash,
    plan: 'free',
    planScreens: 1
  });

  const content1 = await Content.create({
    ownerId: user._id,
    name: 'video-promo.mp4',
    type: 'VIDEO',
    storageUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4',
    durationSeconds: 15
  });

  const content2 = await Content.create({
    ownerId: user._id,
    name: 'banner-academia.jpg',
    type: 'IMAGE',
    storageUrl: 'https://placehold.co/1280x720/38bdf8/0f172a.png',
    durationSeconds: 5
  });

  const content3 = await Content.create({
    ownerId: user._id,
    name: 'video-sneakers.mp4',
    type: 'VIDEO',
    storageUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4',
    durationSeconds: 15
  });

  const playlist = await Playlist.create({
    ownerId: user._id,
    name: 'Playlist Principal',
    items: [
      { contentId: content1._id, name: content1.name, type: content1.type, durationSeconds: 15, order: 0 },
      { contentId: content2._id, name: content2.name, type: content2.type, durationSeconds: 5, order: 1 },
      { contentId: content3._id, name: content3.name, type: content3.type, durationSeconds: 15, order: 2 }
    ]
  });

  const device = await Device.create({
    ownerId: user._id,
    paired: true,
    uniqueId: 'TEST-DEVICE-001',
    name: 'TV Teste',
    model: 'Android TV Box X1',
    status: { online: false, itemCount: 3 }
  });

  await Schedule.create({
    ownerId: user._id,
    name: 'Horário Comercial',
    playlistId: playlist._id,
    deviceIds: [device._id],
    rules: { daysOfWeek: [1, 2, 3, 4, 5], startTime: '08:00', endTime: '18:00' },
    priority: 10,
    active: true
  });

  return true;
}