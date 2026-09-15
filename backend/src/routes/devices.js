import { Router } from 'express';
import Device from '../models/Device.js';
import { authRequired } from '../utils/jwt.js';
import { RealTimeService } from '../services/realtime.js';

const router = Router();
router.use(authRequired);

router.get('/', async (req, res) => {
  try {
    const devices = await Device.find({ ownerId: req.userId }).sort({ updatedAt: -1 });
    res.json({ devices });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/pair', async (req, res) => {
  try {
    const { pairingCode, deviceUniqueId } = req.body;
    if (!pairingCode) return res.status(400).json({ error: 'Código de pareamento obrigatório' });

    const device = await Device.findOne({
      pairingCode,
      pairingCodeExpires: { $gt: new Date() }
    });

    if (!device) {
      return res.status(404).json({ error: 'Código inválido ou expirado' });
    }

    device.ownerId = req.userId;
    device.paired = true;
    device.pairingCode = null;
    device.pairingCodeExpires = null;
    device.name = req.body.name || `TV ${String(device.uniqueId).slice(-4)}`;
    await device.save();

    const manifest = await RealTimeService.sendManifest(device);

    res.json({ device, manifest });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.get('/:id', async (req, res) => {
  try {
    const device = await Device.findOne({ _id: req.params.id, ownerId: req.userId });
    if (!device) return res.status(404).json({ error: 'Dispositivo não encontrado' });
    res.json({ device });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.patch('/:id', async (req, res) => {
  try {
    const allowed = ['name', 'location', 'settings', 'status.volume'];
    const patch = {};
    for (const key of allowed) {
      if (req.body[key] !== undefined) {
        if (key.includes('.')) {
          const [parent, child] = key.split('.');
          patch[parent] = { ...(req.body[parent] || {}), [child]: req.body[key] };
        } else {
          patch[key] = req.body[key];
        }
      }
    }
    const device = await Device.findOneAndUpdate(
      { _id: req.params.id, ownerId: req.userId },
      patch,
      { new: true }
    );
    if (!device) return res.status(404).json({ error: 'Dispositivo não encontrado' });
    res.json({ device });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.delete('/:id', async (req, res) => {
  try {
    const device = await Device.findOneAndDelete({ _id: req.params.id, ownerId: req.userId });
    if (!device) return res.status(404).json({ error: 'Dispositivo não encontrado' });
    RealTimeService.pushCommandToDevice(device._id, { action: 'UNPAIR' });
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/:id/command', async (req, res) => {
  try {
    const device = await Device.findOne({ _id: req.params.id, ownerId: req.userId });
    if (!device) return res.status(404).json({ error: 'Dispositivo não encontrado' });
    const { action } = req.body || {};
    const allowed = ['REBOOT', 'REFRESH', 'PLAY', 'PAUSE', 'STOP', 'VOLUME', 'SCREEN_ON', 'SCREEN_OFF'];
    if (!allowed.includes(action)) {
      return res.status(400).json({ error: `Ação inválida. Use: ${allowed.join(', ')}` });
    }
    RealTimeService.pushCommandToDevice(device._id, { action, value: req.body.value });
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/:id/refresh', async (req, res) => {
  try {
    const device = await Device.findOne({ _id: req.params.id, ownerId: req.userId });
    if (!device) return res.status(404).json({ error: 'Dispositivo não encontrado' });
    const manifest = await RealTimeService.sendManifest(device);
    res.json({ ok: true, manifest });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

export default router;