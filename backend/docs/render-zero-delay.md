# Render: 0 delay (sin cold starts)

Render Free puede dormir el servicio despues de inactividad. Si queres 0 delay real:

## Opcion A (recomendada): pasar a Starter (Always On)

1) Render -> Service -> Settings -> Plan
2) Elegi Starter (Always On)
3) Guarda y redeploy (o espera el auto deploy)

Listo: no hay sleep y el primer request siempre responde sin cold start.

## Opcion B: keep-warm externo (si seguis en Free)

Objetivo: pegarle a `/healthz` cada 5 min.

### UptimeRobot

1) Crear monitor tipo HTTP(s)
2) URL: `https://smartid-backend.onrender.com/healthz`
3) Intervalo: 5 minutes
4) Expected status: 200

### Alternativa: cron externo

Podes usar cualquier cron externo que haga:

```bash
curl -fsS https://smartid-backend.onrender.com/healthz > /dev/null
```

El endpoint `/healthz` no toca DB y responde rapido. Para validar DB usa `/readyz`.
