package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.Device;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.queue.TransferQueue;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class MemoryTypes {
    public static MemoryType GPU_MEM;
    public static MemoryType HOST_MEM;

    public static void createMemoryTypes() {

        for(int i = 0; i < Device.memoryProperties.memoryTypeCount(); ++i) {
            VkMemoryType memoryType = Device.memoryProperties.memoryTypes(i);

            //GPU only Memory
            if(memoryType.propertyFlags() == VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) {
                GPU_MEM = new DeviceLocalMemory(MemoryType.Type.DEVICE_LOCAL, memoryType.propertyFlags());
                //TODO type inside own class
//                GPU_MEM.type = MemoryType.Type.DEVICE_LOCAL;
            }

            if(memoryType.propertyFlags() == (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT)) {
                HOST_MEM = new HostLocalCachedMemory(MemoryType.Type.HOST_LOCAL, memoryType.propertyFlags());
            }
        }

        if(GPU_MEM != null && HOST_MEM != null) return;

        //Could not find 1 or more MemoryTypes, need to use fallback
        if(HOST_MEM == null) {
            HOST_MEM = new HostLocalFallbackMemory(MemoryType.Type.HOST_LOCAL, 0);//TODO!
            if(GPU_MEM != null) return;
        }

        for(int i = 0; i < Device.memoryProperties.memoryTypeCount(); ++i) {
            VkMemoryType memoryType = Device.memoryProperties.memoryTypes(i);

            //gpu-cpu shared memory
            if((memoryType.propertyFlags() & (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) != 0) {
                GPU_MEM = new HostDeviceSharedMemory(MemoryType.Type.HOST_LOCAL, memoryType.propertyFlags());
                return;
            }
        }

        //Could not find device memory, fallback to host memory
        GPU_MEM = HOST_MEM;
    }

    public static class DeviceLocalMemory extends MemoryType {

        public DeviceLocalMemory(Type type, long propertyFlags) {
            super(type, propertyFlags);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {
            MemoryManager.getInstance().createBuffer(buffer, size,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | buffer.usage,
                    VK_MEMORY_HEAP_DEVICE_LOCAL_BIT, buffer.type.mappable());
        }

        @Override
        void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(Renderer.getCurrentFrame());
            stagingBuffer.copyBuffer((int) bufferSize, byteBuffer);

            TransferQueue.INSTANCE.copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), buffer.getUsedBytes(), bufferSize);
        }

        @Override
        void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {

        }

        public long copyBuffer(Buffer src, Buffer dst) {
            if(dst.bufferSize < src.bufferSize) {
                throw new IllegalArgumentException("dst size is less than src size.");
            }

            return TransferQueue.INSTANCE.copyBufferCmd(src.getId(), 0, dst.getId(), 0, src.bufferSize);
        }

        @Override
        void uploadBuffer(Buffer buffer, ByteBuffer byteBuffer) {
            int bufferSize = byteBuffer.remaining();
            StagingBuffer stagingBuffer = Vulkan.getStagingBuffer(Renderer.getCurrentFrame());
            stagingBuffer.copyBuffer(bufferSize, byteBuffer);

            TransferQueue.INSTANCE.copyBufferCmd(stagingBuffer.id, stagingBuffer.offset, buffer.getId(), 0, bufferSize);

        }

        @Override
        boolean mappable() {
            return false;
        }
    }

    static abstract class MappableMemory extends MemoryType {
        public MappableMemory(Type type, long propertyFlags) {
            super(type, propertyFlags);
        }

        @Override
        void copyToBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, (int) buffer.bufferSize), (int) bufferSize, buffer.getUsedBytes());
        }

        @Override
        void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
            VUtil.memcpy(buffer.data.getByteBuffer(0, (int) buffer.bufferSize), byteBuffer, 0);
        }

        @Override
        void uploadBuffer(Buffer buffer, ByteBuffer byteBuffer) {
            VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer(0, (int) buffer.bufferSize), byteBuffer.remaining(), 0);
        }

        @Override
        boolean mappable() {
            return true;
        }
    }

    static class HostLocalCachedMemory extends MappableMemory {
        public HostLocalCachedMemory(Type type, long propertyFlags) {
            super(type, propertyFlags);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {

            MemoryManager.getInstance().createBuffer(buffer, size,
                    buffer.usage,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT, buffer.type.mappable());
        }

        void copyToBuffer(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer byteBuffer) {
            VUtil.memcpy(byteBuffer, buffer.data.getByteBuffer((int) 0, (int) buffer.bufferSize), (int) bufferSize, dstOffset);
        }

        void copyBuffer(Buffer src, Buffer dst) {
            VUtil.memcpy(src.data.getByteBuffer(0, src.bufferSize),
                    dst.data.getByteBuffer(0, dst.bufferSize), src.bufferSize, 0);

//            copyBufferCmd(src.getId(), 0, dst.getId(), 0, src.bufferSize);
        }
    }

    static class HostLocalFallbackMemory extends MappableMemory {
        public HostLocalFallbackMemory(Type type, long propertyFlags) {
            super(type, propertyFlags);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {
            MemoryManager.getInstance().createBuffer(buffer, size,
                    buffer.usage,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, buffer.type.mappable());
        }
    }

    static class HostDeviceSharedMemory extends MappableMemory {
        public HostDeviceSharedMemory(Type type, long propertyFlags) {
            super(type, propertyFlags);
        }

        @Override
        void createBuffer(Buffer buffer, int size) {
            MemoryManager.getInstance().createBuffer(buffer, size,
                    buffer.usage,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, buffer.type.mappable());
        }
    }
}
