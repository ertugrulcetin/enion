npm run clean
npm run release
rm resources/public/js/compiled/app.js.map
rm -rf resources/public/js/compiled/cljs-runtime
rm resources/public/js/compiled/manifest.edn
rm -rf ../enion-backend/resources/public
cp -r resources ../enion-backend
cd ../enion-backend
lein clean
lein uberjar
