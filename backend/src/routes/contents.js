import { Router } from 'express';
import multer from 'multer';
import { authRequired } from '../utils/jwt.js';
import Content from '../models/Content.js';
import User from '../models/User.js';

const router = Router();

router.post('/import', authRequired, multer().single('file'), async (req, res) => {
  try {
    const { file } = req;
    if (!file) return res.status(400).json({ error: 'Arquivo obrigatório' });

    const user = await User.findById(req.userId);
    const name = req.body.name || file.originalname;
    const ext = name.split('.').pop().toLowerCase();
    const type = ['jpg','jpeg','png','gif','webp','bmp'].includes(ext)
      ? 'IMAGE'
      : ['mp3','wav','aac','ogg','m4a','flac'].includes(ext)
        ? 'AUDIO'
        : 'VIDEO';

    let storageUrl = null;
    const firebase = (await import('firebase-admin')).default;
    if (firebase.apps.length && process.env.FIREBASE_STORAGE_BUCKET) {
      const bucket = firebase.storage().bucket();
      const dest = `content/${req.userId}/${Date.now()}_${name}`;
      const blob = bucket.file(dest);
      await new Promise((resolve, reject) => {
        const stream = blob.createWriteStream({ metadata: { contentType: file.mimetype } });
        stream.on('error', reject);
        stream.on('finish', resolve);
        stream.end(file.buffer);
      });
      storageUrl = `https://firebasestorage.googleapis.com/v0/b/${process.env.FIREBASE_STORAGE_BUCKET}/o/${encodeURIComponent(dest)}?alt=media`;
      await blob.makePublic();
      storageUrl = `https://firebasestorage.googleapis.com/v0/b/${process.env.FIREBASE_STORAGE_BUCKET}/o/${encodeURIComponent(dest)}?alt=media`;
    } else {
      storageUrl = `duracao:${req.body.durationSeconds || 0}|local:${name}`;
    }

    const content = await Content.create({
      ownerId: req.userId,
      name,
      type,
      storageUrl,
      storagePath: process.env.FIREBASE_STORAGE_BUCKET ? `content/${req.userId}/${Date.now()}_${name}` : null,
      sizeBytes: file.size,
      durationSeconds: parseInt(req.body.durationSeconds || '0', 10)
    });

    res.status(201).json({ content });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.get('/', authRequired, async (req, res) => {
  try {
    const contents = await Content.find({ ownerId: req.userId }).sort({ createdAt: -1 });
    res.json({ contents });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.delete('/:id', authRequired, async (req, res) => {
  try {
    const content = await Content.findOneAndDelete({ _id: req.params.id, ownerId: req.userId });
    if (!content) return res.status(404).json({ error: 'Conteúdo não encontrado' });
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

export default router;