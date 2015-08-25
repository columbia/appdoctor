EXTRA_TEXT = "android.intent.extra.TEXT"
EXTRA_STREAM = "android.intent.extra.STREAM"
EXTRA_QUERY = "query"

IMAGE_PATH = "/sdcard/DCIM/Camera/picture.jpg"
VIDEO_PATH = "/sdcard/DCIM/Movies/video.mp4"

def obtain_intent_args(intent):
    nargs = 1
    args = ['a', intent['action']]
    action = intent['action']
    data = ''
    mime = ''
    extra_type = ''
    if 'category' in intent:
        for cat in intent['category']:
            args.append('c')
            args.append(cat)
            nargs += 1
    if 'data' in intent:
        if 'scheme' in intent['data']:
            data = intent['data']['scheme'][0] + "://1481"
        if 'mime' in intent['data']:
            mime = ''
            for m in intent['data']['mime']:
                if not mime:
                    mime = m
                elif m.startswith('image'):
                    mime = m
            args.append('m')
            args.append(mime)
            nargs += 1
    if data == '':
        if mime == 'text/plain':
            data = 'test'
            extra_type = 's'
            extra_key = EXTRA_TEXT
            extra_value = data
        elif mime.startswith('image'):
            data = 'file://' + IMAGE_PATH
            extra_type = 'u'
            extra_key = EXTRA_STREAM
            extra_value = data
        elif mime.startswith('video'):
            data = 'file://' + VIDEO_PATH
            extra_type = 'u'
            extra_key = EXTRA_STREAM
            extra_value = data
        elif ("SEND" in action or "ATTACH_DATA" in action
                or "EDIT" in action or "VIEW" in action):
            data = 'file://' + IMAGE_PATH
        elif "SEARCH" in action or "SYSTEM_TUTORIAL" in action:
            extra_type = 's'
            extra_key = EXTRA_QUERY
            extra_value = 'example' # empty query, show search dialog
        else:
            data = 'example'

    if extra_type:
        args.append('e' + extra_type)
        args.append(extra_key)
        args.append(extra_value)
        nargs += 1

    if data:
        args.append('d')
        args.append(data)
        nargs += 1
    return (args, nargs)


