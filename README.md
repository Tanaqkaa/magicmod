# Magic Mod (Fabric 1.21.11)

Этап 1: рывки и перекаты в стиле souls-like.

- **Left Alt** — перекат (на земле, ~0.4 с неуязвимости, сальто-анимация, направление по WASD)
- **X** — рывок (в т.ч. в воздухе, наклон + частицы)
- Клавиши меняются в Настройки → Управление → «Магический мод»

Нужна Java 21. Сборка: `./gradlew build` (Windows: `gradlew.bat build`) → `build/libs/magicmod-0.1.0.jar`
Тест из исходников: `./gradlew runClient`
Без установки чего-либо: залить на GitHub — .github/workflows/build.yml соберёт jar сам (вкладка Actions → Artifacts).
