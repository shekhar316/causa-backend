#!/bin/bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Usage function
usage() {
    local exit_code="${1:-1}"
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Build and push Docker images for causa-backend with multi-architecture support"
    echo "Uses podman buildx + Dockerfile.jvm for multi-arch builds (amd64 + arm64)."
    echo ""
    echo "Options:"
    echo "  -i IMAGE_NAME    Full image name (registry/repository:tag)"
    echo "  -r REGISTRY      Container registry (default: quay.io)"
    echo "  -n REPO_NAME     Repository name (default: rh-ee-shesaxen/causa-backend)"
    echo "  -t TAG           Image tag (default: version from pom.xml) - used only if -i is not provided"
    echo "  -b BUILD         Build image true/false (default: true)"
    echo "  -p PUSH          Push image true/false (default: false)"
    echo "  -l PLATFORMS     Target platforms (default: linux/amd64,linux/arm64)"
    echo "  -c CLEAN         Run clean build true/false (default: true)"
    echo "  -s SKIP_TESTS    Skip tests during build true/false (default: false)"
    echo "  -h               Show this help message"
    echo ""
    echo "Environment Variables (alternative to flags):"
    echo "  IMAGE_NAME       Full image name"
    echo "  REGISTRY         Container registry"
    echo "  REPO_NAME        Repository name"
    echo "  IMAGE_TAG        Image tag (default: version from pom.xml)"
    echo "  BUILD_IMAGE      Build image (true/false)"
    echo "  PUSH_IMAGE       Push image (true/false)"
    echo "  PLATFORMS        Target platforms"
    echo "  CLEAN_BUILD      Clean build (true/false)"
    echo "  SKIP_TESTS       Skip tests (true/false)"
    echo ""
    echo "Examples:"
    echo "  # Build and push with custom full image name"
    echo "  $0 -i quay.io/causa/causa-backend:1.0.0 -b true -p true"
    echo ""
    echo "  # Build only with custom tag (no push)"
    echo "  $0 -t v1.0.0-beta -b true -p false"
    echo ""
    echo "  # Build for AMD64 only and push"
    echo "  $0 -t dev -l linux/amd64 -p true"
    echo ""
    echo "  # Build with custom registry and repo"
    echo "  $0 -r docker.io -n myorg/causa-backend -t latest -p true"
    echo ""
    echo "  # Using environment variables"
    echo "  IMAGE_TAG=v2.0.0 PUSH_IMAGE=true ./build_and_push.sh"
    echo ""
    echo "Note: Command-line flags take precedence over environment variables"
    exit "${exit_code}"
}

# Validate boolean values
validate_boolean() {
    local value="$1"
    local flag="$2"
    if [[ ! "$value" =~ ^(true|false)$ ]]; then
        echo -e "${RED}Error: $flag must be 'true' or 'false', got: '$value'${NC}" >&2
        usage 1
    fi
}

# Print colored message
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Resolve the project root pom.xml relative to this script's location,
# regardless of the working directory the script is invoked from.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Resolve application version from pom.xml (used as the default image tag).
# Uses mvn help:evaluate for an authoritative project.version read — avoids
# accidentally picking up a parent or plugin <version> via grep.
# If the version contains SNAPSHOT, appends a UTC timestamp so each dev build
# gets a unique, sortable tag (e.g. 0.0.1-SNAPSHOT-20250127143012).
resolve_app_version() {
    local pom="${PROJECT_ROOT}/pom.xml"
    local ver="latest"
    if [ -f "${pom}" ]; then
        local mvnw="${PROJECT_ROOT}/mvnw"
        local mvn_cmd="mvn"
        [ -f "${mvnw}" ] && mvn_cmd="${mvnw}"
        ver=$(cd "${PROJECT_ROOT}" && \
              ${mvn_cmd} help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)
        ver="${ver:-latest}"
    fi
    if [[ "$ver" == *SNAPSHOT* ]]; then
        local ts
        ts=$(date -u +"%Y%m%d%H%M%S")
        ver="${ver}-${ts}"
    fi
    echo "$ver"
}

# Default values from environment or hardcoded defaults
REGISTRY="${REGISTRY:-quay.io}"
REPO_NAME="${REPO_NAME:-rh-ee-shesaxen/causa-backend}"
IMAGE_TAG="${IMAGE_TAG:-mvp_demo_2808260130}"
BUILD_IMAGE="${BUILD_IMAGE:-true}"
PUSH_IMAGE="${PUSH_IMAGE:-false}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
CLEAN_BUILD="${CLEAN_BUILD:-true}"
SKIP_TESTS="${SKIP_TESTS:-false}"
IMAGE_NAME="${IMAGE_NAME:-}"

# Parse command line arguments (these override environment variables)
while getopts "i:r:n:t:b:p:l:c:s:h" opt; do
    case ${opt} in
        i )
            IMAGE_NAME="$OPTARG"
            ;;
        r )
            REGISTRY="$OPTARG"
            ;;
        n )
            REPO_NAME="$OPTARG"
            ;;
        t )
            IMAGE_TAG="$OPTARG"
            ;;
        b )
            BUILD_IMAGE="$OPTARG"
            ;;
        p )
            PUSH_IMAGE="$OPTARG"
            ;;
        l )
            PLATFORMS="$OPTARG"
            ;;
        c )
            CLEAN_BUILD="$OPTARG"
            ;;
        s )
            SKIP_TESTS="$OPTARG"
            ;;
        h )
            usage 0
            ;;
        \? )
            print_error "Invalid option: -$OPTARG"
            usage 1
            ;;
    esac
done

# Validate boolean flags
validate_boolean "$BUILD_IMAGE" "BUILD_IMAGE (-b)"
validate_boolean "$PUSH_IMAGE" "PUSH_IMAGE (-p)"
validate_boolean "$CLEAN_BUILD" "CLEAN_BUILD (-c)"
validate_boolean "$SKIP_TESTS" "SKIP_TESTS (-s)"

# If IMAGE_NAME is not provided via -i or env, construct it from components
if [ -z "$IMAGE_NAME" ]; then
    IMAGE_NAME="${REGISTRY}/${REPO_NAME}:${IMAGE_TAG}"
fi

# Validate project root structure
if [ ! -f "${PROJECT_ROOT}/pom.xml" ]; then
    print_error "pom.xml not found at ${PROJECT_ROOT}."
    exit 1
fi

# Check if Maven wrapper exists
if [ ! -f "${PROJECT_ROOT}/mvnw" ]; then
    print_error "Maven wrapper (mvnw) not found at ${PROJECT_ROOT}."
    exit 1
fi

# Check for podman (required for multi-arch build)
if ! command -v podman &>/dev/null; then
    print_error "podman is not installed or not on PATH. Multi-arch builds require podman."
    exit 1
fi

DOCKERFILE="${PROJECT_ROOT}/src/main/docker/Dockerfile.jvm"
if [ ! -f "${DOCKERFILE}" ]; then
    print_error "Dockerfile not found at ${DOCKERFILE}."
    exit 1
fi

# Run all Maven commands from the project root
cd "${PROJECT_ROOT}"

# Make Maven wrapper executable
chmod +x ./mvnw

# Display configuration
echo ""
print_info "=== Build Configuration ==="
print_info "Image Name:      ${IMAGE_NAME}"
print_info "Build:           ${BUILD_IMAGE}"
print_info "Push:            ${PUSH_IMAGE}"
print_info "Platforms:       ${PLATFORMS}"
print_info "Clean Build:     ${CLEAN_BUILD}"
print_info "Skip Tests:      ${SKIP_TESTS}"
print_info "Dockerfile:      ${DOCKERFILE}"
echo ""

# Warn if pushing is enabled
if [ "$PUSH_IMAGE" = "true" ]; then
    print_warn "Push is enabled. Image will be pushed to registry."
    print_warn "Make sure you are authenticated to ${REGISTRY}"
    echo ""
fi

# ── Step 1: Maven package (compile + package only, no container build) ────────
MAVEN_CMD="./mvnw"

if [ "$CLEAN_BUILD" = "true" ]; then
    MAVEN_CMD="${MAVEN_CMD} clean"
fi

MAVEN_CMD="${MAVEN_CMD} package -Dquarkus.container-image.build=false"

if [ "$SKIP_TESTS" = "true" ]; then
    MAVEN_CMD="${MAVEN_CMD} -DskipTests"
fi

print_info "Step 1/2 — Maven package"
print_info "Executing: ${MAVEN_CMD}"
echo ""
eval "${MAVEN_CMD}"

# ── Step 2: podman buildx multi-arch build (and optional push) ────────────────
if [ "$BUILD_IMAGE" = "true" ]; then
    echo ""
    print_info "Step 2/2 — podman buildx multi-arch image build"

    # Convert comma-separated platforms to the format podman expects (already correct)
    PODMAN_PLATFORMS="${PLATFORMS}"

    # Manifest name is the full image name; podman buildx will create/replace it.
    MANIFEST_NAME="${IMAGE_NAME}"

    # Remove any stale local manifest with the same name so podman doesn't error.
    podman manifest rm "${MANIFEST_NAME}" 2>/dev/null || true

    PODMAN_CMD="podman buildx build"
    PODMAN_CMD="${PODMAN_CMD} --platform ${PODMAN_PLATFORMS}"
    PODMAN_CMD="${PODMAN_CMD} --manifest ${MANIFEST_NAME}"
    PODMAN_CMD="${PODMAN_CMD} -f ${DOCKERFILE}"
    PODMAN_CMD="${PODMAN_CMD} ${PROJECT_ROOT}"

    print_info "Executing: ${PODMAN_CMD}"
    echo ""
    eval "${PODMAN_CMD}"

    if [ "$PUSH_IMAGE" = "true" ]; then
        echo ""
        print_info "Pushing multi-arch manifest to registry..."
        podman manifest push --all "${MANIFEST_NAME}" "docker://${MANIFEST_NAME}"
    fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
print_info "=== Build Summary ==="
print_info "✓ Maven package completed successfully"

if [ "$BUILD_IMAGE" = "true" ]; then
    print_info "✓ Multi-arch image built: ${IMAGE_NAME}"
    print_info "  Platforms: ${PLATFORMS}"

    if [ "$PUSH_IMAGE" = "true" ]; then
        print_info "✓ Image pushed to registry"
    else
        print_warn "Image was built but not pushed (PUSH_IMAGE=false)"
    fi
fi
echo ""
