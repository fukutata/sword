FROM nginx:alpine

COPY web/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O- http://localhost/ >/dev/null || exit 1
