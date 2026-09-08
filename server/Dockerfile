FROM python:3.12-alpine
WORKDIR /app
COPY sync_server.py .
ENV PORT=8088
EXPOSE 8088
CMD ["python3", "sync_server.py"]
