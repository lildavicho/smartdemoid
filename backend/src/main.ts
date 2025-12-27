import { NestFactory } from '@nestjs/core'
import { ValidationPipe } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { AppModule } from './app.module'

async function bootstrap() {
  const app = await NestFactory.create(AppModule)

  const configService = app.get(ConfigService)

  app.enableCors({
    origin: true,
    methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
    credentials: true,
  })

  const httpAdapter = app.getHttpAdapter()
  const instance: any = httpAdapter.getInstance?.()
  if (instance?.set) instance.set('trust proxy', 1)

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: true },
    }),
  )

  const apiPrefix = configService.get<string>('API_PREFIX') || 'api/v1'
  app.setGlobalPrefix(apiPrefix)

  app.use('/healthz', (_req: any, res: any) => res.status(200).send('ok'))

  const port = Number(process.env.PORT) || configService.get<number>('PORT') || 3000
  await app.listen(port, '0.0.0.0')

  console.log(`SmartPresence Backend running: http://0.0.0.0:${port}/${apiPrefix}`)
}

bootstrap()
