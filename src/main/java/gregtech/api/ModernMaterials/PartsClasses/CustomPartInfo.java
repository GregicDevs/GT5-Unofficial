package gregtech.api.ModernMaterials.PartsClasses;

import static gregtech.api.ModernMaterials.PartProperties.Textures.TextureType.Metallic;

import gregtech.api.ModernMaterials.PartProperties.Textures.TextureType;

public class CustomPartInfo {

    public final PartsEnum mPart;
    private TextureType mTextureType = Metallic;
    private String mTextureName;
    private boolean mAnimated;

    public CustomPartInfo(PartsEnum aPart) {
        mPart = aPart;
        mTextureName = aPart.partName;
    }

    public TextureType getmTextureType() {
        return mTextureType;
    }

    public CustomPartInfo setTextureType(final TextureType aTextureType) {
        mTextureType = aTextureType;
        return this;
    }

    public boolean isNotAnimated() {
        return !mAnimated;
    }

    public CustomPartInfo enableCustomTexture() {
        mAnimated = true;
        return this;
    }

    public CustomPartInfo setCustomPartTextureOverride(final String aFileName) {
        mAnimated = true;
        mTextureName = aFileName;
        return this;
    }

    public String getmTextureName() {
        return mTextureName;
    }
}