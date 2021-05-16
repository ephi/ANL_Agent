import glob
import json
import numpy as np
import matplotlib.pyplot as plt
import re


def ANL_Analyzer(anl_res_path='C:/DEV/Java/ANL/DockerRunner/results', show_all=True):
    aul = {}
    neg_files = glob.glob(anl_res_path + '/*_negotiation.json')
    regex = re.compile(r'([A-Z])\w+')
    for f_p in neg_files:
        if show_all:
            ul = {}
        with open(f_p) as f:
            neg_file_dict = json.load(f)
            for action in neg_file_dict["SAOPState"]["actions"]:
                if show_all:
                    if "offer" in action:
                        utils = action["offer"]["utilities"]
                        for key, val in utils.items():
                            if key in ul:
                                ul[key].append(val)
                            else:
                                ul[key] = [val]
                if "accept" in action:
                    utils = action["accept"]["utilities"]
                    for key, val in utils.items():
                        if show_all:
                            if key in ul:
                                ul[key].append(val)
                            else:
                                ul[key] = [val]
                        if key in aul:
                            aul[key].append(val)
                        else:
                            aul[key] = [val]
        if show_all:
            for key, val in ul.items():
                m = regex.search(key)
                plt.plot(np.array(val), label=m.group())
            plt.legend(loc='upper left', borderaxespad=0.)
            plt.ylabel("Util")
            plt.title(f_p)
            plt.xlabel("Turn (last turn is accepted)")
            plt.show()
    a_aul = {}
    for key, val in aul.items():
        m = regex.search(key)
        np_val = np.array(val)
        a_aul[key] = np.average(np_val)
        plt.plot(np_val, label=m.group())
    plt.legend(loc='upper left', borderaxespad=0.)
    plt.ylabel("Accepted Utils")
    plt.title("Accepted Utils in all negotiations")
    plt.xlabel("Negotiation #")
    a_aul = dict(sorted(a_aul.items(),
                        key=lambda item: item[1],
                        reverse=True))
    print("Average Utils:\n")
    print(a_aul)
    plt.show()


# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    ANL_Analyzer()

# See PyCharm help at https://www.jetbrains.com/help/pycharm/
