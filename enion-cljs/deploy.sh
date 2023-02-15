npm run clean
npm run release
rm -rf resources/public/js/compiled/cljs-runtime
rm resources/public/js/compiled/manifest.edn
cp -r resources ../enion-backend
cd ../enion-backend
lein clean
lein uberjar
fly deploy
