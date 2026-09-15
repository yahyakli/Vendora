FROM node:22-alpine

WORKDIR /app

RUN apk add --no-cache wget

COPY package*.json ./
RUN npm ci --omit=dev

COPY src ./src

ENV NODE_ENV=production
ENV PORT=8084
EXPOSE 8084

CMD ["node", "src/index.js"]
