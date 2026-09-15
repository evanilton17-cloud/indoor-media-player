import { Router } from 'express';
import { authRequired } from '../utils/jwt.js';
import Schedule from '../models/Schedule.js';
import Playlist from '../models/Playlist.js';
import Device from '../models/Device.js';
import { RealTimeService } from '../services/realtime.js';

const router = Router();
router.use(authRequired);

router.get('/', async (req, res) => {
  try {
    const schedules = await Schedule.find({ ownerId: req.userId })
      .sort({ priority: -1 })
      .populate('playlistId', 'name');
    res.json({ schedules });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/', async (req, res) => {
  try {
    const { name, playlistId, deviceIds, rules, priority } = req.body || {};
    if (!name || !playlistId) {
      return res.status(400).json({ error: 'name e playlistId obrigatórios' });
    }
    const playlist = await Playlist.findOne({ _id: playlistId, ownerId: req.userId });
    if (!playlist) return res.status(404).json({ error: 'Playlist não encontrada' });

    const devices = await Device.find({
      _id: { $in: deviceIds || [] },
      ownerId: req.userId
    });

    const schedule = await Schedule.create({
      ownerId: req.userId,
      name,
      playlistId,
      deviceIds: devices.map((d) => d._id),
      rules: {
        daysOfWeek: rules?.daysOfWeek ?? [0,1,2,3,4,5,6],
        startTime: rules?.startTime ?? '00:00',
        endTime: rules?.endTime ?? '23:59',
        startDate: rules?.startDate,
        endDate: rules?.endDate
      },
      priority: priority ?? 1
    });

    for (const device of devices) {
      RealTimeService.notifyManifestUpdate(device._id);
    }

    res.status(201).json({ schedule });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.patch('/:id', async (req, res) => {
  try {
    const schedule = await Schedule.findOne({ _id: req.params.id, ownerId: req.userId });
    if (!schedule) return res.status(404).json({ error: 'Agendamento não encontrado' });

    if (req.body.name) schedule.name = req.body.name;
    if (req.body.priority !== undefined) schedule.priority = req.body.priority;
    if (req.body.rules) schedule.rules = { ...schedule.rules, ...req.body.rules };
    if (req.body.active !== undefined) schedule.active = req.body.active;
    if (req.body.playlistId) {
      const p = await Playlist.findOne({ _id: req.body.playlistId, ownerId: req.userId });
      if (p) schedule.playlistId = p._id;
    }
    if (req.body.deviceIds) {
      const devices = await Device.find({ _id: { $in: req.body.deviceIds }, ownerId: req.userId });
      schedule.deviceIds = devices.map((d) => d._id);
    }
    await schedule.save();

    for (const deviceId of schedule.deviceIds) {
      RealTimeService.notifyManifestUpdate(deviceId);
    }
    res.json({ schedule });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.delete('/:id', async (req, res) => {
  try {
    const schedule = await Schedule.findOneAndDelete({ _id: req.params.id, ownerId: req.userId });
    if (!schedule) return res.status(404).json({ error: 'Agendamento não encontrado' });
    for (const deviceId of schedule.deviceIds) {
      RealTimeService.notifyManifestUpdate(deviceId);
    }
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.get('/proof-of-play', async (req, res) => {
  try {
    const { deviceId, from, to } = req.query;
    const filter = { ownerId: req.userId };
    if (deviceId) filter.deviceId = deviceId;
    if (from || to) {
      filter.startedAt = {};
      if (from) filter.startedAt.$gte = new Date(from);
      if (to) filter.startedAt.$lte = new Date(to);
    }
    const logs = await (await import('../models/ProofOfPlay.js')).default
      .find(filter)
      .sort({ startedAt: -1 })
      .limit(500)
      .select('deviceId contentName type startedAt endedAt durationSeconds');
    res.json({ logs });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

export default router;