# Small, single-stage image — no build step, no database, no persistence
# (docs/adr/0010-simulated-consumers).
FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install --omit=dev
COPY server.js ./
EXPOSE 4000
CMD ["node", "server.js"]
