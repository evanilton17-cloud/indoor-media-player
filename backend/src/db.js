import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { config } from 'dotenv';

config();

let memoryServer = null;

export async function connectDatabase() {
  const uri = process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/indoor_media';

  try {
    await mongoose.connect(uri, { serverSelectionTimeoutMS: 3000 });
    console.log(`MongoDB conectado: ${uri}`);
    return 'mongo';
  } catch (e) {
    console.log(`MongoDB real indisponível (${e.message})...`);
    console.log('Iniciando MongoDB em memória para demonstração...');
    memoryServer = await MongoMemoryServer.create();
    const memUri = memoryServer.getUri();
    await mongoose.connect(memUri);
    console.log(`MongoDB em memória conectado: ${memUri}`);
    return 'memory';
  }
}

export async function getMemoryServer() {
  return memoryServer;
}

export async function disconnectDatabase() {
  await mongoose.disconnect();
  if (memoryServer) {
    await memoryServer.stop();
    memoryServer = null;
  }
}