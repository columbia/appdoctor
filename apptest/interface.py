import connection
import base64
import logging
import exception
import time
import intentlib

logger = logging.getLogger("interface")
auto_wait_idle = False

START_ATTEMPT = 5
START_INTERVAL = 3

def action(func):
    def wrap(*args, **kwargs):
        ret = func(*args, **kwargs)
        if (auto_wait_idle):
            wait_for_idle()
        return ret
    return wrap

def start():
    result = None
    exc = None
    for i in range(START_ATTEMPT):
        try:
            conn = connection.get_conn()
            result = conn.wait_result(conn.send_event("Start"))
            break
        except exception.ConnectionBroken as e:
            exc = e
            if "reset" in e.message:
                logger.info("start failed: conn. reset. retry %d" % i)
                connection.close_conn()
                time.sleep(START_INTERVAL)
            else:
                raise e
    if result is None:
        raise exc

    ins_path = result[0]
    ins_path = base64.b64decode(ins_path)
    wait_for_idle()

    return ins_path

@action
def click(widget_id):
    (pos_x, pos_y) = get_widget_target(widget_id)
    mouse_click(pos_x, pos_y)

@action
def long_click(widget_id):
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("LongClick", widget_id))

def get_widget_target(widget_id):
    view_info = dump_view(widget_id)
    pos_x = view_info[0] + view_info[2] / 2
    pos_y = view_info[1] + min(10, view_info[3] / 2)
    return (pos_x, pos_y)

@action
def enter(widget_id, text):
    conn = connection.get_conn()
    click(widget_id)
    conn.send_event_sync("Input", base64.b64encode(text))

def get_text(widget_id):
    conn = connection.get_conn()
    conn.send_event("get_text", widget_id)

@action
def key_down(key):
    conn = connection.get_conn()
    conn.send_event("KeyDown", key)

@action
def key_up(key):
    conn = connection.get_conn()
    conn.send_event("KeyUp", key)

@action
def mouse_down(widget_id, x, y):
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("MouseDown", x, y))

@action
def mouse_up(widget_id, x, y):
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("MouseUp", x, y))

def wait_for_idle():
    conn = connection.get_conn()
    event_id = conn.send_event("WaitForIdle")
    conn.wait_result(event_id)

@action
def mouse_click(x, y):
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("PointerClickOnPoint", x, y))

def dump_view(widget_id):
    conn = connection.get_conn()
    event_id = conn.send_event("GetViewGeo", widget_id)
    result = conn.wait_result(event_id)
    xresult = []
    for i in result:
        xresult += [int(i)]

    return xresult

@action
def set_text(widget_id, text):
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("SetViewText", widget_id, base64.b64encode(text)))

def get_view_by_class(cls):
    conn = connection.get_conn()
    event_id = conn.send_event("FindViewByClass", cls)
    result = conn.wait_result(event_id)
    try:
        int(result[0], 16)
        return result[0]
    except:
        return None

def get_view_child(view_id, entry):
    conn = connection.get_conn()
    event_id = conn.send_event("GetViewChild", view_id, entry)
    result = conn.wait_result(event_id)
    try:
        int(result[0], 16)
        return result[0]
    except:
        return None

def get_view_child_by_text(view_id, text):
    raise Exception("not implemented yet")

def finish():
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("Finish"))

def set_auto_wait(auto_wait):
    global auto_wait_idle
    auto_wait_idle = auto_wait

def dump_views(wait = True):
    conn = connection.get_conn()
    conn.send_line("- DumpViews")
    if wait:
        conn.wait_result('-')

@action
def menu_click(menu_id):
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("MenuClick", menu_id))

def get_widget_id(name):
    conn = connection.get_conn()
    result = conn.wait_result(conn.send_event("GetViewId", name))
    int(result[0], 16)
    return result[0]

@action
def rotate():
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("Rotate"))

@action
def crawl():
    conn = connection.get_conn()
    conn.send_event("Crawl")

@action
def select(op):
    conn = connection.get_conn()
    conn.wait_result(conn.send_event("Select", op))

def collect():
    conn = connection.get_conn()
    return conn.wait_result(conn.send_event("Collect"))

def enable_checker():
    conn = connection.get_conn()
    conn.send_event("EnableChecker")

def disable_checker():
    conn = connection.get_conn()
    conn.send_event("DisableChecker")

def close():
    connection.close_conn()

def hint_edit(edit_id, contents):
    hint_widget("Edit", edit_id, contents)

def hint_widget(type_, widget_id, contents):
    conn = connection.get_conn()
    content_encoded = []
    for content in contents:
        content_encoded.append(base64.b64encode(content.encode('utf-8')))
    conn.send_event("Hint" + type_, widget_id, len(content_encoded), *content_encoded)

def unhint_edit(edit_id):
    conn = connection.get_conn()
    conn.send_event("UnhintEdit", edit_id)

def hint_btn(btn_id, contents):
    hint_widget("Btn", btn_id, contents)

def should_change_dpi():
    conn = connection.get_conn()
    return True if conn.wait_result(conn.send_event("ShouldChangeDpi"))[0] == "YES" else False

def change_dpi(dpi):
    conn = connection.get_conn()
    conn.send_event("ChangeDpi", dpi)

def purge_checkers():
    conn = connection.get_conn()
    conn.send_event("RemoveAllCheckers", "View")
    conn.send_event("RemoveAllCheckers", "General")
    conn.send_event("RemoveAllCheckers", "Orientation")

def add_checker(cls, checker):
    conn = connection.get_conn()
    conn.send_event("AddChecker", cls, checker)

def enable_lcc():
    conn = connection.get_conn()
    conn.send_event("EnableLifeCycleChecker")

def notify_intent(intent):
    assert 'activity' in intent
    (args, nargs) = intentlib.obtain_intent_args(intent)
    args.append('comp')
    args.append(intent['activity']['name'])
    nargs += 1
    conn = connection.get_conn()
    conn.send_event("AddIntent", 0.01, 1, nargs, *args)

def notify_broadcast(intent):
    assert 'receiver' in intent
    (args, nargs) = intentlib.obtain_intent_args(intent)
    conn = connection.get_conn()
    conn.send_event("AddBroadcast", 0.01, 1, nargs, *args)


