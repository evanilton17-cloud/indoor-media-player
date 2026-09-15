import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'change-me-in-production';

export function signToken(user) {
  return jwt.sign({ id: user._id, email: user.email, role: user.role }, JWT_SECRET, {
    expiresIn: '7d'
  });
}

export function verifyToken(token) {
  return jwt.verify(token, JWT_SECRET);
}

export function authRequired(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Token ausente' });
  }
  try {
    const payload = verifyToken(header.slice(7));
    req.userId = payload.id;
    req.userRole = payload.role;
    next();
  } catch (e) {
    res.status(401).json({ error: 'Token inválido ou expirado' });
  }
}