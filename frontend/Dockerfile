# Expects pre-built dist/ (run `npm run build` first with VITE_FIREBASE_* env vars set).

FROM nginx:1.27-alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
