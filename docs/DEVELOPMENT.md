# Development Guidlines

## Creating a Release
You have two options to create a release based on the workflows I created:

Option 1: Push a Tag (Automatic Release)

This uses the build.yml workflow which auto-releases when a tag is pushed:

### Make sure all changes are committed
git add .                                                                                                                                                                                                                             
git commit -m "Prepare for release v1.0.0"

### Create and push a tag
git tag v1.0.0                                                                                                                                                                                                                        
git push origin master                                                                                                                                                                                                                
git push origin v1.0.0

The workflow will:
1. Build installers on all 3 platforms
2. Automatically create a GitHub Release with all artifacts attached

Option 2: Manual Release (Workflow Dispatch)

This uses the release.yml workflow:

1. Go to your GitHub repo → Actions tabexitcla
2. Select "Release" workflow on the left
3. Click "Run workflow" button
4. Enter the version (e.g., 1.0.0) and whether it's a pre-release
5. Click "Run workflow"

This will build, create a tag, and publish the release.      