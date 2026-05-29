#!/bin/bash
# =============================================================================
# Create Release Script
# =============================================================================
# Creates a new GitHub release with proper tagging for rollback support.
#
# Usage:
#   ./scripts/create-release.sh [version] [message]
#
# Examples:
#   ./scripts/create-release.sh                    # Auto-increment from v1.1
#   ./scripts/create-release.sh v1.2.0             # Specific version
#   ./scripts/create-release.sh v1.2.0 "Stable release with AI features"
#
# Prerequisites:
#   - GitHub CLI (gh) authenticated
#   - Clean working directory (no uncommitted changes)
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get current version from latest tag
get_current_version() {
    git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0"
}

# Increment version (patch level)
increment_version() {
    local version=$1
    # Remove 'v' prefix
    version=${version#v}
    # Split by dots
    IFS='.' read -r major minor patch <<< "$version"
    # Increment patch
    patch=$((patch + 1))
    echo "v${major}.${minor}.${patch}"
}

# Main function
main() {
    local current_version=$(get_current_version)
    local new_version=${1:-$(increment_version "$current_version")}
    local message=${2:-"Release $new_version"}

    echo -e "${GREEN}Creating release: $new_version${NC}"
    echo -e "${YELLOW}Current version: $current_version${NC}"
    echo ""

    # Check for uncommitted changes
    if [ -n "$(git status --porcelain)" ]; then
        echo -e "${RED}ERROR: Working directory is not clean.${NC}"
        echo "Please commit or stash your changes first."
        exit 1
    fi

    # Check if version tag already exists
    if git rev-parse "$new_version" >/dev/null 2>&1; then
        echo -e "${RED}ERROR: Tag $new_version already exists.${NC}"
        exit 1
    fi

    # Check GitHub CLI authentication
    if ! gh auth status >/dev/null 2>&1; then
        echo -e "${RED}ERROR: GitHub CLI not authenticated.${NC}"
        echo "Run: gh auth login"
        exit 1
    fi

    # Create annotated tag
    echo -e "${YELLOW}Creating tag: $new_version${NC}"
    git tag -a "$new_version" -m "$message"

    # Push tag
    echo -e "${YELLOW}Pushing tag to remote...${NC}"
    git push origin "$new_version"

    # Create GitHub release
    echo -e "${YELLOW}Creating GitHub release...${NC}"
    gh release create "$new_version" \
        --title "$new_version" \
        --notes "$message" \
        --latest

    echo ""
    echo -e "${GREEN}✓ Release $new_version created successfully!${NC}"
    echo ""
    echo "Rollback options:"
    echo "  - By version: Use '$new_version' in rollback workflow"
    echo "  - By commit:  Use 'sha-$(git rev-parse --short HEAD)' in rollback workflow"
    echo ""
    echo "Next steps:"
    echo "  1. Verify the release on GitHub"
    echo "  2. Test rollback workflow (optional)"
    echo "  3. Continue development with confidence!"
}

main "$@"
