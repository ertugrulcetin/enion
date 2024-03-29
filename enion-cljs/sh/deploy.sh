export ENION_SHA=$(git rev-parse HEAD)
npm run clean
npm run release
sentry-cli releases --org enion-online --project enion-online new ${ENION_SHA}
sentry-cli releases --org enion-online --project enion-online files ${ENION_SHA} upload-sourcemaps resources/public/js/compiled --validate --url-prefix "~/js/compiled"
sentry-cli releases --org enion-online --project enion-online finalize ${ENION_SHA}
rm resources/public/js/compiled/app.js.map
rm -rf resources/public/js/compiled/cljs-runtime
rm resources/public/js/compiled/manifest.edn
rm -rf ../enion-backend/resources/public
cp -r resources ../enion-backend
cd ../enion-backend
lein clean
lein uberjar

fly deploy --local-only --image-label ${ENION_SHA}

fly deploy -a enion-eu-1 --local-only --image-label ${ENION_SHA}
#fly deploy -a enion-eu-1 --local-only --image-label ${ENION_SHA} & \
#fly deploy -a enion-eu-2 --local-only --image-label ${ENION_SHA} & \
#fly deploy -a enion-eu-3 --local-only --image-label ${ENION_SHA} & \
#fly deploy -a enion-br-1 --local-only --image-label ${ENION_SHA} & \
#fly deploy -a enion-br-2 --local-only --image-label ${ENION_SHA} &
