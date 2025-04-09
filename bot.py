import logging
from aiogram import Bot, Dispatcher, types
from aiogram.types import Message
from aiogram.utils import executor
from aiogram.contrib.middlewares.logging import LoggingMiddleware
import os

API_TOKEN = os.getenv("TELEGRAM_API_TOKEN", "your_token_here")

logging.basicConfig(level=logging.INFO)
bot = Bot(token=API_TOKEN)
dp = Dispatcher(bot)
dp.middleware.setup(LoggingMiddleware())

@dp.message_handler(commands=['start'])
async def cmd_start(message: Message):
    await message.reply("Добро пожаловать! Введите /help, чтобы узнать доступные команды.")

@dp.message_handler(commands=['help'])
async def cmd_help(message: Message):
    await message.reply_text(
        "Команды:
"
        "/start — начать работу с ботом
"
        "/help — помощь по использованию
"
        "/add — добавить новый путевой лист
"
        "/list — список путевых листов
"
        "/export — экспортировать в PDF
"
        "/report — отчёт за период"
    )

@dp.message_handler(commands=['add'])
async def cmd_add(message: Message):
    await message.reply("Добавление путевого листа пока в разработке.")

@dp.message_handler(commands=['list'])
async def cmd_list(message: Message):
    await message.reply("Список путевых листов пока пуст.")

@dp.message_handler(commands=['export'])
async def cmd_export(message: Message):
    await message.reply("Экспорт в PDF в процессе разработки.")

@dp.message_handler(commands=['report'])
async def cmd_report(message: Message):
    await message.reply("Введите период отчета (например, 01.01.2024 - 31.01.2024).")

if __name__ == '__main__':
    executor.start_polling(dp, skip_updates=True)