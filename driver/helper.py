import sys
import controller
import checker

def main(args):
    id = args[0]
    cmd = args[1]
    if cmd == "check":
        checker.main(["testcase/" + id, id])
    else:
        yaml_file = "testcase/" + id + "/" + id + ".yaml"
        case_file = "testcase/" + id + "/case-" + id + ".json"
        controller.main([yaml_file, case_file, cmd])


if __name__ == "__main__":
    main(sys.argv[1:])
