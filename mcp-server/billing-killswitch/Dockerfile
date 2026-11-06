FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=8080
COPY package.json index.js ./
EXPOSE 8080
USER node
CMD ["node", "index.js"]
