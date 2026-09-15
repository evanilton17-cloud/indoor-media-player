import Device from '../models/Device.js';
import Content from '../models/Content.js';
import Playlist from '../models/Playlist.js';
import Schedule from '../models/Schedule.js';

let io = null;
const deviceSockets = new Map();

export class RealTimeService {
  static init(ioInstance) {
    io = ioInstance;
    io.on('connection', (socket) => {
      socket.on('device:hello', async (payload, cb) => {
        try {
          const { uniqueId, appVersion, model, androidVersion } = payload || {};
          if (!uniqueId) {
            return typeof cb === 'function' && cb({ error: 'uniqueId obrigatório' });
          }
          let device = await Device.findOne({ uniqueId });
          if (!device) {
            device = await Device.create({ uniqueId, appVersion, model, androidVersion });
          } else {
            device.appVersion = appVersion;
            device.model = model;
            device.androidVersion = androidVersion;
            device.status.lastSeen = new Date();
            await device.save();
          }
          deviceSockets.set(socket.id, String(device._id));
          socket.join(`device:${device._id}`);
          const pairing = !device.paired;
          const reply = { deviceId: String(device._id), needPairing: pairing };
          if (pairing) {
            reply.pairingCode = await RealTimeService.issuePairingCode(device);
          }
          typeof cb === 'function' && cb(reply);
        } catch (e) {
          typeof cb === 'function' && cb({ error: e.message });
        }
      });

      socket.on('device:status', (data) => {
        const deviceId = deviceSockets.get(socket.id);
        if (!deviceId) return;
        RealTimeService.updateStatus(deviceId, data);
      });

      socket.on('device:proof', (data) => {
        const deviceId = deviceSockets.get(socket.id);
        if (!deviceId) return;
        RealTimeService.recordProof(deviceId, data);
      });

      socket.on('device:pairing', async (data, cb) => {
        const deviceId = deviceSockets.get(socket.id);
        if (!deviceId) return;
        try {
          const device = await Device.findById(deviceId);
          if (!device) return;
          device.pairingCode = await RealTimeService.makePairingCode();
          device.pairingCodeExpires = new Date(Date.now() + 10 * 60 * 1000);
          await device.save();
          typeof cb === 'function' && cb({ pairingCode: device.pairingCode });
        } catch (e) {
          typeof cb === 'function' && cb({ error: e.message });
        }
      });

      socket.on('disconnect', async () => {
        const deviceId = deviceSockets.get(socket.id);
        if (deviceId) {
          deviceSockets.delete(socket.id);
          Device.findByIdAndUpdate(deviceId, {
            'status.online': false,
            'status.lastSeen': new Date()
          }).exec();
        }
      });
    });
  }

  static async issuePairingCode(device) {
    const code = await RealTimeService.makePairingCode();
    device.pairingCode = code;
    device.pairingCodeExpires = new Date(Date.now() + 10 * 60 * 1000);
    await device.save();
    return code;
  }

  static async makePairingCode() {
    return String(Math.floor(100000 + Math.random() * 900000));
  }

  static async updateStatus(deviceId, data) {
    const update = { 'status.lastSeen': new Date(), 'status.online': true };
    if (data.currentContent) update['status.currentContent'] = data.currentContent;
    if (data.currentZone) update['status.currentZone'] = data.currentZone;
    if (typeof data.itemCount === 'number') update['status.itemCount'] = data.itemCount;
    if (data.volume) update['status.volume'] = data.volume;
    await Device.findByIdAndUpdate(deviceId, update).exec();
  }

  static async recordProof(deviceId, data) {
    const device = await Device.findById(deviceId).select('ownerId').exec();
    if (!device || !device.ownerId) return;
    const { contentId, contentName, type, startedAt, endedAt, durationSeconds } = data || {};
    const reference = `${deviceId}_${startedAt || Date.now()}`;
    try {
      await import('../models/ProofOfPlay.js').then(async ({ default: ProofOfPlay }) => {
        const exists = await ProofOfPlay.findOne({ reference });
        if (exists) return;
        await ProofOfPlay.create({
          ownerId: device.ownerId,
          deviceId,
          contentId,
          contentName,
          type,
          startedAt,
          endedAt,
          durationSeconds,
          reference
        });
      });
    } catch (e) {
      console.error('Erro ao gravar proof of play:', e.message);
    }
  }

  static async pushStreamToDevice(deviceId, manifest) {
    io?.to(`device:${deviceId}`).emit('stream:manifest', manifest);
  }

  static async pushCommandToDevice(deviceId, command) {
    io?.to(`device:${deviceId}`).emit('device:command', command);
  }

  static async sendManifest(device) {
    if (!device.ownerId) return {};
    const now = new Date();
    const schedule = await Schedule.findOne({
      ownerId: device.ownerId,
      deviceIds: device._id,
      active: true,
      'rules.startDate': { $lte: now },
      $or: [{ 'rules.endDate': { $exists: false } }, { 'rules.endDate': { $gte: now } }]
    }).sort({ priority: -1 });

    let playlist = null;
    if (schedule?.playlistId) {
      playlist = await Playlist.findById(schedule.playlistId);
    }
    const playlistId = schedule?.playlistId ?? device.settings?.playlistId;

    if (!playlist && playlistId) {
      playlist = await Playlist.findById(playlistId);
    }
    if (!playlist) {
      return { schedule: null, items: [] };
    }

    const itemsWithUrls = [];
    for (const it of playlist.items) {
      const content = await Content.findById(it.contentId).exec();
      if (content) {
        itemsWithUrls.push({
          id: it.contentId,
          name: content.name,
          type: content.type,
          storageUrl: content.storageUrl,
          storagePath: content.storagePath,
          durationSeconds: it.durationSeconds || content.durationSeconds,
          version: content.version
        });
      }
    }
    const manifest = {
      schedule: schedule ? { id: schedule._id, name: schedule.name } : null,
      playlistId: playlistId ? String(playlistId) : null,
      items: itemsWithUrls,
      generatedAt: now.toISOString()
    };
    RealTimeService.pushStreamToDevice(device._id, manifest);
    return manifest;
  }

  static async notifyManifestUpdate(deviceId) {
    const device = await Device.findById(deviceId).exec();
    if (device) RealTimeService.sendManifest(device);
  }
}