import { Router } from 'express';
import { authRequired } from '../utils/jwt.js';
import Playlist from '../models/Playlist.js';
import Content from '../models/Content.js';

const router = Router();
router.use(authRequired);

router.get('/', async (req, res) => {
  try {
    const playlists = await Playlist.find({ ownerId: req.userId }).sort({ createdAt: -1 });
    res.json({ playlists });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/', async (req, res) => {
  try {
    const { name, description, items } = req.body || {};
    if (!name) return res.status(400).json({ error: 'name obrigatório' });

    const playlistItems = [];
    for (const it of items || []) {
      const content = await Content.findOne({ _id: it.contentId, ownerId: req.userId });
      if (content) {
        playlistItems.push({
          contentId: content._id,
          name: content.name,
          type: content.type,
          durationSeconds: it.durationSeconds || content.durationSeconds
        });
      }
    }
    const playlist = await Playlist.create({
      ownerId: req.userId,
      name,
      description,
      items: playlistItems.map((it, idx) => ({ ...it, order: idx }))
    });
    res.status(201).json({ playlist });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.patch('/:id', async (req, res) => {
  try {
    const playlist = await Playlist.findOne({ _id: req.params.id, ownerId: req.userId });
    if (!playlist) return res.status(404).json({ error: 'Playlist não encontrada' });
    if (req.body.name) playlist.name = req.body.name;
    if (req.body.description !== undefined) playlist.description = req.body.description;
    if (req.body.items) {
      const items = [];
      for (const it of req.body.items) {
        const content = await Content.findOne({ _id: it.contentId, ownerId: req.userId });
        if (content) {
          items.push({
            contentId: content._id,
            name: content.name,
            type: content.type,
            durationSeconds: it.durationSeconds || content.durationSeconds,
            order: items.length
          });
        }
      }
      playlist.items = items;
    }
    await playlist.save();
    res.json({ playlist });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.delete('/:id', async (req, res) => {
  try {
    await Playlist.findOneAndDelete({ _id: req.params.id, ownerId: req.userId });
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

export default router;