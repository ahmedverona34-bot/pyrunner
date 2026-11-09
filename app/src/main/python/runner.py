import builtins
import sys
import traceback

_globals = {"__name__": "__main__"}


class _Writer:
    def __init__(self, bridge, err):
        self.bridge = bridge
        self.err = err

    def write(self, s):
        s = str(s)
        if s:
            self.bridge.onOutput(s, self.err)
        return len(s)

    def flush(self):
        pass

    def isatty(self):
        return False


def run(code, bridge):
    old = sys.stdout, sys.stderr, builtins.input
    sys.stdout = _Writer(bridge, False)
    sys.stderr = _Writer(bridge, True)
    builtins.input = lambda prompt="": str(bridge.onInput(str(prompt)))
    try:
        try:
            compiled = compile(code, "<code>", "eval")
        except SyntaxError:
            exec(compile(code, "<code>", "exec"), _globals)
        else:
            result = eval(compiled, _globals)
            if result is not None:
                print(repr(result))
    except SystemExit:
        pass
    except BaseException:
        traceback.print_exc()
    finally:
        sys.stdout, sys.stderr, builtins.input = old


def reset():
    _globals.clear()
    _globals["__name__"] = "__main__"
