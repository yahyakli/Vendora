FROM python:3.10-slim

WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends libgomp1 wget \
	&& rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY scripts ./scripts
COPY models ./models

ENV PYTHONPATH=/app
ENV PORT=8085
EXPOSE 8085

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085"]
