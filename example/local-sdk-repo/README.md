# Локальный RC для MOBILE-389

Артефакты, опубликованные командой `publishToMavenLocal` с `feature/MOBILE-389`
(коммит 9ab13f30) под версией `2.15.5-mobile389-local`. Использовать вместо
реального релиза на Maven Central, чтобы собрать example на CI с изменениями
из MOBILE-389/MOBILE-321 без выпуска настоящего RC наружу.

Эта ветка/папка — одноразовая. После публикации example в Google Play удалить
ветку и не мержить в develop.

Пересобрать: из корня android-sdk на нужном коммите —
`CI=false ./gradlew publishToMavenLocal -PSDK_VERSION_NAME=<версия>`, затем
скопировать `~/.m2/repository/cloud/mindbox/{mobile-sdk,mindbox-common,
mindbox-common-android,mindbox-firebase,mindbox-huawei,mindbox-rustore}`
сюда, в `local-sdk-repo/cloud/mindbox/`.
