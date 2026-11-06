#include <errno.h>
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <poll.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/ioctl.h>

#define USBIP_JNI(name) Java_com_limelight_binding_input_driver_UsbIpNative_##name
#define MAX_ISO_PACKETS 128

/** Return the physical connection's negotiated USB speed, or a negative Linux errno. */
JNIEXPORT jint JNICALL USBIP_JNI(getSpeed)(JNIEnv *env, jclass cls, jint fd) {
    int speed = ioctl(fd, USBDEVFS_GET_SPEED);
    return speed < 0 ? -errno : speed;
}

/** Submit an asynchronous USB request. The kernel owns the buffer until reap or fd close. */
JNIEXPORT jlong JNICALL USBIP_JNI(submit)(JNIEnv *env, jclass cls, jint fd, jint type,
                                         jint endpoint, jbyteArray data, jintArray lengths) {
    const jsize length = (*env)->GetArrayLength(env, data);
    const jsize count = lengths ? (*env)->GetArrayLength(env, lengths) : 0;
    if (count > MAX_ISO_PACKETS || length > 65536 + (type == 0 ? 8 : 0)
            || (type == 1) != (count > 0)) {
        return -EINVAL;
    }

    struct usbdevfs_urb *urb = calloc(1, sizeof(*urb) + count * sizeof(urb->iso_frame_desc[0]));
    if (!urb) {
        return -ENOMEM;
    }
    urb->buffer = calloc(1, length ? length : 1);
    if (!urb->buffer) {
        free(urb);
        return -ENOMEM;
    }

    // Android endpoint types differ from usbfs URB types.
    switch (type) {
        case 0:
            if (length < 8) {
                free(urb->buffer);
                free(urb);
                return -EINVAL;
            }
            urb->type = USBDEVFS_URB_TYPE_CONTROL;
            break;
        case 1:
            urb->type = USBDEVFS_URB_TYPE_ISO;
            // The virtual Windows bus and Android USB bus have different frame clocks.
            urb->flags = USBDEVFS_URB_ISO_ASAP;
            break;
        case 2:
            urb->type = USBDEVFS_URB_TYPE_BULK;
            break;
        case 3:
            urb->type = USBDEVFS_URB_TYPE_INTERRUPT;
            break;
        default:
            free(urb->buffer);
            free(urb);
            return -EINVAL;
    }
    urb->endpoint = endpoint;
    urb->buffer_length = length;
    urb->number_of_packets = count;
    if (count) {
        jint packets[MAX_ISO_PACKETS];
        (*env)->GetIntArrayRegion(env, lengths, 0, count, packets);
        for (jsize i = 0; i < count; i++) {
            urb->iso_frame_desc[i].length = packets[i];
        }
    }
    if ((type == 0 || !(endpoint & 0x80)) && length) {
        (*env)->GetByteArrayRegion(env, data, 0, length, urb->buffer);
    }

    if (ioctl(fd, USBDEVFS_SUBMITURB, urb) < 0) {
        int error = errno;
        free(urb->buffer);
        free(urb);
        return -error;
    }
    return (jlong)(uintptr_t)urb;
}

/** Wait for the next usbfs completion across all endpoints on this fd. */
JNIEXPORT jlong JNICALL USBIP_JNI(reap)(JNIEnv *env, jclass cls, jint fd) {
    struct pollfd wait = {.fd = fd, .events = POLLOUT};
    int ready = poll(&wait, 1, 250);
    if (ready <= 0) {
        return ready == 0 || errno == EINTR ? 0 : -errno;
    }

    struct usbdevfs_urb *urb = NULL;
    if (ioctl(fd, USBDEVFS_REAPURBNDELAY, &urb) < 0) {
        return errno == EAGAIN || errno == EINTR ? 0 : -errno;
    }
    return (jlong)(uintptr_t)urb;
}

/** Copy a reaped request's status and optionally its IN data into Java-owned memory. */
JNIEXPORT void JNICALL USBIP_JNI(readResult)(JNIEnv *env, jclass cls, jlong handle,
                                             jintArray result, jbyteArray data) {
    const struct usbdevfs_urb *urb = (const struct usbdevfs_urb *)(uintptr_t)handle;
    jint values[3 + 2 * MAX_ISO_PACKETS] = {urb->status, urb->actual_length, urb->start_frame};
    for (int i = 0; i < urb->number_of_packets; i++) {
        values[3 + 2 * i] = urb->iso_frame_desc[i].actual_length;
        values[4 + 2 * i] = urb->iso_frame_desc[i].status;
    }
    (*env)->SetIntArrayRegion(env, result, 0, 3 + 2 * urb->number_of_packets, values);
    if (data && urb->buffer_length > 0) {
        // ISO packet slots retain their original size even when individual packets are short.
        (*env)->SetByteArrayRegion(env, data, 0, urb->buffer_length, urb->buffer);
    }
}

/** Ask usbfs to cancel a request. A cancelled request still has to be reaped. */
JNIEXPORT jboolean JNICALL USBIP_JNI(cancel)(JNIEnv *env, jclass cls, jint fd, jlong handle) {
    return ioctl(fd, USBDEVFS_DISCARDURB, (void *)(uintptr_t)handle) == 0;
}

/** Release the native storage after the kernel has finished accessing it. */
JNIEXPORT void JNICALL USBIP_JNI(free)(JNIEnv *env, jclass cls, jlong handle) {
    struct usbdevfs_urb *urb = (struct usbdevfs_urb *)(uintptr_t)handle;
    free(urb->buffer);
    free(urb);
}
