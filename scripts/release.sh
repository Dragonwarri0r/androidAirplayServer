#!/bin/bash

# Android AirPlay Server Release Script
# This script helps create releases for the project

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if version is provided
if [ -z "$1" ]; then
    print_error "Version number is required!"
    echo "Usage: $0 <version> [--dry-run]"
    echo "Example: $0 v1.2.0"
    exit 1
fi

VERSION=$1
DRY_RUN=false

# Check for dry-run flag
if [ "$2" = "--dry-run" ]; then
    DRY_RUN=true
    print_warning "Running in dry-run mode - no changes will be made"
fi

# Validate version format
if [[ ! $VERSION =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    print_error "Version must be in format v1.2.3"
    exit 1
fi

print_info "Creating release for version: $VERSION"

# Check if we're in the right directory
if [ ! -f "build.gradle" ] || [ ! -f "settings.gradle" ]; then
    print_error "Please run this script from the project root directory"
    exit 1
fi

# Check if git is clean
if [ "$DRY_RUN" = false ]; then
    if ! git diff-index --quiet HEAD --; then
        print_error "Git working directory is not clean. Please commit or stash your changes."
        exit 1
    fi
fi

# Update version in build.gradle
print_info "Updating version in build.gradle..."
VERSION_NUMBER=${VERSION#v}  # Remove 'v' prefix
if [ "$DRY_RUN" = false ]; then
    sed -i.bak "s/versionName \".*\"/versionName \"$VERSION_NUMBER\"/" app/build.gradle
    rm app/build.gradle.bak 2>/dev/null || true
else
    print_info "Would update versionName to: $VERSION_NUMBER"
fi

# Update CHANGELOG.md
print_info "Updating CHANGELOG.md..."
if [ "$DRY_RUN" = false ]; then
    # Create a backup
    cp CHANGELOG.md CHANGELOG.md.bak
    
    # Replace [Unreleased] with version and date
    TODAY=$(date +%Y-%m-%d)
    sed -i.tmp "s/## \[Unreleased\]/## [$VERSION] - $TODAY/" CHANGELOG.md
    rm CHANGELOG.md.tmp
    
    # Add new [Unreleased] section at the top
    sed -i.tmp "/^## \[$VERSION\]/i\\
\\
## [Unreleased]\\
\\
### Added\\
### Changed\\
### Fixed\\
### Removed\\
" CHANGELOG.md
    rm CHANGELOG.md.tmp
else
    print_info "Would update CHANGELOG.md with version: $VERSION"
fi

# Clean and build
print_info "Cleaning and building project..."
if [ "$DRY_RUN" = false ]; then
    ./gradlew clean
    ./gradlew assembleDebug assembleRelease
else
    print_info "Would run: ./gradlew clean assembleDebug assembleRelease"
fi

# Run tests
print_info "Running tests..."
if [ "$DRY_RUN" = false ]; then
    ./gradlew testDebugUnitTest
else
    print_info "Would run: ./gradlew testDebugUnitTest"
fi

# Git operations
if [ "$DRY_RUN" = false ]; then
    print_info "Committing changes..."
    git add app/build.gradle CHANGELOG.md
    git commit -m "chore: bump version to $VERSION"
    
    print_info "Creating git tag..."
    git tag -a "$VERSION" -m "Release $VERSION"
    
    print_info "Pushing changes and tag..."
    git push origin main
    git push origin "$VERSION"
else
    print_info "Would commit changes and create tag: $VERSION"
    print_info "Would push to origin/main and origin/$VERSION"
fi

# APK information
if [ "$DRY_RUN" = false ]; then
    DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
    RELEASE_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
    
    print_success "Release created successfully!"
    echo ""
    print_info "Generated APKs:"
    if [ -f "$DEBUG_APK" ]; then
        echo "  Debug:   $DEBUG_APK"
        echo "  Size:    $(du -h "$DEBUG_APK" | cut -f1)"
    fi
    if [ -f "$RELEASE_APK" ]; then
        echo "  Release: $RELEASE_APK"
        echo "  Size:    $(du -h "$RELEASE_APK" | cut -f1)"
    fi
    
    echo ""
    print_info "Next steps:"
    echo "1. The tag has been pushed to GitHub"
    echo "2. GitHub Actions will automatically create a release"
    echo "3. APKs will be attached to the GitHub release"
    echo "4. Review the release on GitHub and publish if everything looks good"
else
    print_success "Dry run completed successfully!"
    echo ""
    print_info "This script would:"
    echo "1. Update version to $VERSION_NUMBER in app/build.gradle"
    echo "2. Update CHANGELOG.md with release date"
    echo "3. Build debug and release APKs"
    echo "4. Run tests"
    echo "5. Commit changes and create git tag"
    echo "6. Push to GitHub to trigger release"
fi

print_success "Done!" 