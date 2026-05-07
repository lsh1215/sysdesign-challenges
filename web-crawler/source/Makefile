.PHONY: up down logs smoke build

build:
	docker compose build

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f

smoke: up
	bash scripts/smoke.sh
