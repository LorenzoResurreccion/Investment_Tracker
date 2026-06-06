# AWS Deployment Guide

This document covers the one-time setup needed to deploy the Investment Tracker to AWS. Once set up, all deployments happen automatically via GitHub Actions on push to `main`.

## Domains

| Purpose | Domain | Points to |
|---------|--------|-----------|
| Front-end | `investmenttracker.lorenzoresurreccion.com` | CloudFront distribution |
| Back-end API + WebSocket | `api-invtracker.lorenzoresurreccion.com` | Lightsail static IP |

## Architecture

```
investmenttracker.lorenzoresurreccion.com
    │
    └── CloudFront (CDN + HTTPS)
            └── S3 Bucket (Front-end static files)

api-invtracker.lorenzoresurreccion.com
    │
    └── Lightsail Instance ($7/month)
            ├── nginx (reverse proxy + SSL via Let's Encrypt)
            ├── Spring Boot JAR (port 8080)
            └── PostgreSQL (self-hosted on same instance)
```

## Cost

| Service | Cost |
|---------|------|
| Lightsail instance (1 GB RAM, 2 vCPUs) | $7/month |
| S3 + CloudFront | < $1/month |
| Domain (already owned) | $0 |
| **Total** | **~$8/month** |

## One-Time Setup

### Step 1: Create Lightsail Instance

1. Go to Lightsail console → Create instance
2. Region: `us-east-1`
3. OS: Ubuntu 22.04
4. Plan: $7/month (1 GB RAM, 2 vCPUs)
5. Name: `investment-tracker`
6. Download the default SSH key pair
7. After creation: go to Networking → Create static IP → Attach to this instance

### Step 2: Configure the Instance

SSH in:

```bash
ssh -i lightsail-key.pem ubuntu@<instance-ip>
```

Install dependencies:

```bash
# Java 21
sudo apt update
sudo apt install -y openjdk-21-jre-headless

# PostgreSQL
sudo apt install -y postgresql
sudo systemctl enable postgresql

# nginx
sudo apt install -y nginx
```

Set up the database:

```bash
sudo -u postgres createuser ubuntu --createdb
createdb investment_tracker
```

Create the app directory:

```bash
sudo mkdir -p /opt/app
sudo chown ubuntu:ubuntu /opt/app
```

### Step 3: Create .env on the Instance

```bash
cat > /opt/app/.env << 'EOF'
FINNHUB_API_KEY=your_finnhub_api_key
DATABASE_URL=jdbc:postgresql://localhost:5432/investment_tracker
DATABASE_USERNAME=ubuntu
DATABASE_PASSWORD=
SERVER_PORT=8080
FRONTEND_ORIGIN=https://investmenttracker.lorenzoresurreccion.com
EOF
```

### Step 4: Create systemd Service

```bash
sudo tee /etc/systemd/system/investment-tracker.service > /dev/null << 'EOF'
[Unit]
Description=Investment Tracker
After=network.target postgresql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -Xmx256m -jar investment-tracker.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable investment-tracker
```

The service will start once the first deploy pushes the JAR.

### Step 5: Configure nginx

```bash
sudo tee /etc/nginx/sites-available/investment-tracker > /dev/null << 'EOF'
server {
    listen 80;
    server_name api-invtracker.lorenzoresurreccion.com;

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /ws/ {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/investment-tracker /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

### Step 6: Open Ports in Lightsail

In the Lightsail console → instance → Networking tab:
- Port 80 (HTTP) — already open by default
- Port 443 (HTTPS) — add this
- Port 22 (SSH) — already open, restrict to your IP if you want

### Step 7: SSL with Let's Encrypt

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api-invtracker.lorenzoresurreccion.com
```

Auto-renews via systemd timer. Free.

### Step 8: Create S3 Bucket

1. Go to S3 console → Create bucket
2. Name: `investment-tracker-frontend` (or your domain name)
3. Region: same as Lightsail instance
4. Block all public access: YES (CloudFront uses OAC)
5. Create bucket

Enable static hosting:
- Properties → Static website hosting → Enable
- Index document: `index.html`
- Error document: `index.html`

### Step 9: Create CloudFront Distribution

1. Go to CloudFront console → Create distribution
2. Origin: select your S3 bucket
3. Origin access: Origin Access Control (create new OAC)
4. Default root object: `index.html`
5. Custom error responses:
   - 403 → `/index.html` → HTTP 200
   - 404 → `/index.html` → HTTP 200
6. Price class: Use only North America and Europe
7. Alternate domain: `investmenttracker.lorenzoresurreccion.com`
8. SSL certificate: request one via ACM for `investmenttracker.lorenzoresurreccion.com` (free, must be in us-east-1)

After creation, copy the bucket policy CloudFront gives you and paste it into S3 → bucket → Permissions → Bucket policy.

### Step 10: Create IAM User for CI/CD

1. Go to IAM console → Users → Create user
2. Name: `github-deploy`
3. Attach permissions (inline policy):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:DeleteObject", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::investment-tracker-frontend",
        "arn:aws:s3:::investment-tracker-frontend/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": "cloudfront:CreateInvalidation",
      "Resource": "arn:aws:cloudfront::YOUR_ACCOUNT_ID:distribution/YOUR_DISTRIBUTION_ID"
    }
  ]
}
```

4. Create access key (for "Application running outside AWS")
5. Save the Access Key ID and Secret — you'll need them next

### Step 11: Configure GitHub Secrets

Go to your GitHub repo → Settings → Secrets and variables → Actions → New repository secret.

Add these:

| Secret | Value |
|--------|-------|
| `LIGHTSAIL_SSH_KEY` | Full contents of your Lightsail private key (.pem file) |
| `LIGHTSAIL_HOST` | Lightsail static IP (e.g., `44.211.189.246`) |
| `LIGHTSAIL_USER` | `ubuntu` |
| `AWS_ACCESS_KEY_ID` | From IAM user you just created |
| `AWS_SECRET_ACCESS_KEY` | From IAM user you just created |
| `AWS_REGION` | `us-east-1` |
| `S3_BUCKET` | `investment-tracker-frontend` |
| `CLOUDFRONT_DISTRIBUTION_ID` | From CloudFront console (e.g., `E1A2B3C4D5`) |
| `VITE_API_BASE_URL` | `https://api-invtracker.lorenzoresurreccion.com/api` |
| `VITE_WS_URL` | `wss://api-invtracker.lorenzoresurreccion.com/ws/prices` |

### Step 12: Deploy

Push to `main`. GitHub Actions will automatically:
1. Run all tests
2. Build the JAR and front-end
3. Upload the JAR to Lightsail and restart the service
4. Sync front-end files to S3
5. Invalidate the CloudFront cache

Done. Every future push to `main` deploys automatically.

### Step 9.5: Configure DNS

In your DNS provider for `lorenzoresurreccion.com`, add these records:

| Type | Name | Value |
|------|------|-------|
| CNAME | `investmenttracker` | Your CloudFront distribution domain (e.g., `d1234abcdef.cloudfront.net`) |
| A | `api-invtracker` | Your Lightsail static IP (e.g., `44.211.189.246`) |

The ACM certificate validation (from Step 9) will also require a CNAME record — AWS shows you the exact record to add during the certificate request process.

**Note**: CNAME records can take a few minutes to propagate. You can verify with:
```bash
dig investmenttracker.lorenzoresurreccion.com
dig api-invtracker.lorenzoresurreccion.com
```

## Maintenance

**View logs:**
```bash
ssh -i lightsail-key.pem ubuntu@<instance-ip>
journalctl -u investment-tracker -f
```

**Database backups:**
```bash
pg_dump investment_tracker > backup_$(date +%Y%m%d).sql
```

**Update instance packages:**
```bash
sudo apt update && sudo apt upgrade -y
```

**Restart the app manually:**
```bash
sudo systemctl restart investment-tracker
```

## Checklist

- [ ] Lightsail instance created and SSH working
- [ ] Java 21, PostgreSQL, nginx installed
- [ ] Database created (`investment_tracker`)
- [ ] `/opt/app/.env` configured with Finnhub key
- [ ] systemd service file created and enabled
- [ ] nginx configured with reverse proxy
- [ ] Ports 80/443 open in Lightsail networking
- [ ] SSL configured (Let's Encrypt or skip for initial testing)
- [ ] S3 bucket created
- [ ] CloudFront distribution created with OAC
- [ ] IAM user created with S3 + CloudFront permissions
- [ ] All GitHub Secrets added
- [ ] First push to `main` triggers successful deploy
