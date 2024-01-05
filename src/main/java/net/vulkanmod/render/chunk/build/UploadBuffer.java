import net.vulkanmod.render.chunk.util.Util;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class UploadBuffer {
    private static final int BUFFER_SIZE = 1024; // Adjust the buffer size as needed

    public final int indexCount;
    public final boolean autoIndices;
    public final boolean indexOnly;
    private final ByteBuffer vertexBuffer;
    private final ByteBuffer indexBuffer;

    // debug
    private boolean released = false;

    private static final ByteBuffer reusableBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

    public UploadBuffer(TerrainBufferBuilder.RenderedBuffer renderedBuffer) {
        TerrainBufferBuilder.DrawState drawState = renderedBuffer.drawState();
        this.indexCount = drawState.indexCount();
        this.autoIndices = drawState.sequentialIndex();
        this.indexOnly = drawState.indexOnly();

        if (!this.indexOnly) {
            this.vertexBuffer = reuseBuffer(renderedBuffer.vertexBuffer());
        } else {
            this.vertexBuffer = null;
        }

        if (!drawState.sequentialIndex()) {
            this.indexBuffer = reuseBuffer(renderedBuffer.indexBuffer());
        } else {
            this.indexBuffer = null;
        }
    }

    private ByteBuffer reuseBuffer(ByteBuffer sourceBuffer) {
        reusableBuffer.clear();
        reusableBuffer.put(sourceBuffer);
        reusableBuffer.flip();
        ByteBuffer targetBuffer = MemoryUtil.memAlloc(reusableBuffer.capacity());
        targetBuffer.put(reusableBuffer);
        targetBuffer.flip();
        return targetBuffer;
    }

    public int indexCount() {
        return indexCount;
    }

    public ByteBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public ByteBuffer getIndexBuffer() {
        return indexBuffer;
    }

    public void release() {
        if (!released) {
            if (vertexBuffer != null) {
                MemoryUtil.memFree(vertexBuffer);
            }
            if (indexBuffer != null) {
                MemoryUtil.memFree(indexBuffer);
            }
            released = true;
        }
    }
}
