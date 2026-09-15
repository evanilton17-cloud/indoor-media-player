import express from 'express';
import http from 'http';
import cors from 'cors';
import mongoose from 'mongoose';
import dotenv from 'dotenv';
import { Server } from 'socket.io';
import authRoutes from './routes/auth.js';
import deviceRoutes from './routes/devices.js';
import contentRoutes from './routes/contents.js';
import playlistRoutes from './routes/playlists.js';
import scheduleRoutes from './routes/schedules.js';
import { RealTimeService } from './services/realtime.js';
import { connectDatabase } from './db.js';
import { seedDemoData } from './services/demoseed.js';

dotenv.config();

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });

app.use(cors());
app.use(express.json());
app.use(express.static('public'));

app.use('/api/auth', authRoutes);
app.use('/api/devices', deviceRoutes);
app.use('/api/contents', contentRoutes);
app.use('/api/playlists', playlistRoutes);
app.use('/api/schedules', scheduleRoutes);
app.get('/api/health', (req, res) => res.json({ ok: true }));

RealTimeService.init(io);

const PORT = process.env.PORT || 3000;

connectDatabase().then(async (mode) => {
  if (mode === 'memory') {
    await seedDemoData();
    console.log('Dados de demonstração criados (admin@test.com / 123456)');
  }
  server.listen(PORT, () => {
    console.log(`Servidor rodando na porta ${PORT}`);
  });
});