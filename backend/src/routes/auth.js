import { Router } from 'express';
import bcrypt from 'bcryptjs';
import User from '../models/User.js';
import { signToken } from '../utils/jwt.js';

const router = Router();

router.post('/register', async (req, res) => {
  try {
    const { name, email, password } = req.body;
    if (!name || !email || !password) {
      return res.status(400).json({ error: 'name, email e password são obrigatórios' });
    }
    const exists = await User.findOne({ email: email.toLowerCase() });
    if (exists) {
      return res.status(409).json({ error: 'Email já cadastrado' });
    }
    const hash = await bcrypt.hash(password, 10);
    const user = await User.create({ name, email, password: hash, plan: 'free', planScreens: 1 });
    const token = signToken(user);
    res.status(201).json({
      token,
      user: { id: user._id, name: user.name, email: user.email, plan: user.plan, planScreens: user.planScreens }
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    const user = await User.findOne({ email: (email || '').toLowerCase() });
    if (!user || !(await bcrypt.compare(password || '', user.password))) {
      return res.status(401).json({ error: 'Credenciais inválidas' });
    }
    if (!user.active) {
      return res.status(403).json({ error: 'Conta desativada' });
    }
    const token = signToken(user);
    res.json({
      token,
      user: { id: user._id, name: user.name, email: user.email, plan: user.plan, planScreens: user.planScreens }
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/upgrade', (req, res) => {
  res.json({
    message: 'Integração com Stripe/mercadopago aqui. O plano é liberado após pagamento.',
    plans: [
      { name: 'free', screens: 1, price: 0 },
      { name: 'starter', screens: 5, priceMonthly: 19.9 },
      { name: 'pro', screens: 20, priceMonthly: 49.9 },
      { name: 'enterprise', screens: 100, priceMonthly: 199.9 }
    ]
  });
});

export default router;