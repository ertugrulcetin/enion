export ENION_SHA=$(git rev-parse HEAD)
npm run clean
npm run release
sentry-cli releases --org enion-online --project enion-online new ${ENION_SHA}
sentry-cli releases --org enion-online --project enion-online files ${ENION_SHA} upload-sourcemaps resources/public/js/compiled --validate --url-prefix "~/compiled"
sentry-cli releases --org enion-online --project enion-online finalize ${ENION_SHA}
rm resources/public/js/compiled/app.js.map
rm -rf resources/public/js/compiled/cljs-runtime
rm resources/public/js/compiled/manifest.edn
rm -rf ../enion-backend/resources/public
cp -r resources ../enion-backend
cd ../enion-backend
lein clean
lein uberjar
fly deploy --local-only
