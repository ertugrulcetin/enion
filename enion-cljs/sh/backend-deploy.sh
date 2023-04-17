export ENION_SHA=$(git rev-parse HEAD)
cd ../enion-backend
lein clean
lein uberjar

fly deploy --local-only --image-label ${ENION_SHA}

fly deploy -a enion-eu-1 --local-only --image-label ${ENION_SHA}
#fly deploy -a enion-eu-2 --local-only --image-label ${ENION_SHA} & \
#fly deploy -a enion-eu-3 --local-only --image-label ${ENION_SHA} & \
#fly deploy -a enion-br-1 --local-only --image-label ${ENION_SHA} & \
#fly deploy -a enion-br-2 --local-only --image-label ${ENION_SHA} &
