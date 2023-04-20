# UI
npm run clean
npm run release
rm resources/public/js/compiled/app.js.map
rm -rf resources/public/js/compiled/cljs-runtime
rm resources/public/js/compiled/manifest.edn

# Get the current time in milliseconds
current_time=$(date +%s%3N)
# Compress the resources/public directory using zip
cd resources
zip -r ../enion-$current_time.zip public
cd ..
# Move the compressed file to the ~/Desktop directory
mv enion-$current_time.zip ~/Desktop/

# Backend
rm -rf ../enion-backend/resources/public
cp -r resources ../enion-backend
cd ../enion-backend
lein clean
lein uberjar
