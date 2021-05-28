import glob
import json
import os

import numpy as np
import matplotlib.pyplot as plt
import ntpath


def ANL_Build_Docker_Learning(deadline, learning_info):
    learning_str = f"- learn:\n    deadline: {deadline}\n    parties:\n"
    learning_str = learning_str.format(deadline)
    for (party, info) in learning_info:
        learning_str = learning_str + f"    - party: {party}" + "\n"
        per_state, nego_data = info
        learning_str = learning_str + "      parameters: {" + f"persistentstate: {per_state}, negotiationdata: ["
        for nego_session in nego_data[:-1]:
            learning_str = learning_str + f"{nego_session}, "
        learning_str = learning_str + f"{nego_data[-1]}]"
        learning_str = learning_str + "}\n"
    return learning_str


def ANL_Build_Docker_Negotiation(deadline, negotiation_info):
    negotiation_str = f"- negotiation:\n    deadline: {deadline}\n    parties:\n"
    negotiation_str = negotiation_str.format(deadline)
    for (party, profile, param) in negotiation_info:
        negotiation_str = negotiation_str + f"    - party: {party}\n      profile: {profile}\n"
        if param is not None:
            per_state, nego_data = param
            negotiation_str = negotiation_str + "      parameters: {" + f"persistentstate: {per_state}, negotiationdata: ["
            negotiation_str = negotiation_str + f"{nego_data}]"
            negotiation_str = negotiation_str + "}\n"
    return negotiation_str


def ANL_Build_Learn_Session(deadline, learning_session_cntr, learning_session_learn_cntr, party_name, party_path, isP2 = False):
    if learning_session_cntr % 5 == 0:
        learn_sessions = []
        while learning_session_learn_cntr < learning_session_cntr:
            if isP2:
                learn_sessions.append(party_name + "_p2_session_" + str(learning_session_learn_cntr))
            else:
                learn_sessions.append(party_name + "_session_" + str(learning_session_learn_cntr))
            learning_session_learn_cntr += 1
        if len(learn_sessions) > 0:
            state_party_name = party_name + "_state"
            if isP2:
                state_party_name = party_name + "_p2_state"
            nego_parm = [(party_path, (state_party_name, learn_sessions))]
            learn_str = ANL_Build_Docker_Learning(deadline, nego_parm)
            return learn_str, learning_session_learn_cntr
    return None


def ANL_Build_Docker_Settings(docker_path="C:\\DEV\\Java\ANL\\DockerRunner", negotiation_deadline=3):
    parties_path = docker_path + "\\parties"
    profile_path = docker_path + "\\profiles"
    parties_files = glob.glob(parties_path + '\\*.jar')
    profile_paths = [f.path for f in os.scandir(profile_path) if f.is_dir()]
    p1_learning_session_cntr = 1
    p1_learning_session_learn_cntr = 1

    p2_learning_session_cntr = 1
    p2_learning_session_learn_cntr = 1

    total_scenarios_cnt = 0
    with open('settings.yml', 'w') as writer:
        for k, prof_path in enumerate(profile_paths):
            profile_files = glob.glob(prof_path + '\\*[0-9].json', recursive=False)
            if len(profile_files) == 0:
                print("invalid prof path: " + prof_path +"\n")
                continue
            prof_name = os.path.basename(profile_files[0])
            prof_path_0 = "profiles/" + prof_path.split('\\')[-1] + "/" + prof_name
            prof_name = os.path.basename(profile_files[1])
            prof_path_1 = "profiles/" + prof_path.split('\\')[-1] + "/" + prof_name
            print(prof_path)
            for i, p1 in enumerate(parties_files):
                p1_name = ntpath.basename(p1)
                p1_name = p1_name[0: p1_name.find("-"): 1]
                p1_path_name = "parties/" + os.path.basename(p1)
                for j, p2 in enumerate(parties_files):
                    if i != j:
                        p2_path_name = "parties/" + os.path.basename(p2)
                        p2_name = ntpath.basename(p2)
                        p2_name = p2_name[0: p2_name.find("-"): 1]
                        nego_param_p1 = None
                        nego_param_p2 = None
                        if "learn" in p1:
                            nego_param_p1 = (p1_name + "_state", p1_name + "_session_" + str(p1_learning_session_cntr))
                            p1_learning_session_cntr += 1
                        if "learn" in p2:
                            nego_param_p2 = (
                                p2_name + "_p2_state", p2_name + "_p2_session_" + str(p2_learning_session_cntr))
                            p2_learning_session_cntr += 1
                        p1_tuffle = (p1_path_name, prof_path_0, nego_param_p1)
                        p2_tuffle = (p2_path_name, prof_path_1, nego_param_p2)
                        negotiation_info = [p1_tuffle, p2_tuffle]
                        neg_str = ANL_Build_Docker_Negotiation(negotiation_deadline, negotiation_info)
                        writer.write(neg_str)
                        print(neg_str)
                        learn_str, learning_cntr_update = ANL_Build_Learn_Session(60,
                                                                                     p1_learning_session_cntr,
                                                                                     p1_learning_session_learn_cntr,
                                                                                     p1_name, p1_path_name) or (None, None)
                        if learn_str is not None:
                            p1_learning_session_learn_cntr = learning_cntr_update
                            writer.write(learn_str)
                            print(learn_str)


                        learn_str, learning_cntr_update = ANL_Build_Learn_Session(60,
                                                                                     p2_learning_session_cntr,
                                                                                     p2_learning_session_learn_cntr,
                                                                                     p2_name, p2_path_name,True) or (None, None)
                        if learn_str is not None:
                            p2_learning_session_learn_cntr = learning_cntr_update
                            writer.write(learn_str)
                            print(learn_str)
                        total_scenarios_cnt += 1
    print(total_scenarios_cnt)


def ANL_Analyzer(anl_res_path='C:/DEV/Java/ANL/DockerRunner/results', show_all=False):
    aul = {}
    neg_files = glob.glob(anl_res_path + '/*_negotiation.json')
    for f_p in neg_files:
        if show_all:
            ul = {}
        with open(f_p) as f:
            neg_file_dict = json.load(f)
            had_acceptence = False
            for action in neg_file_dict["SAOPState"]["actions"]:
                if show_all:
                    if "offer" in action:
                        utils = action["offer"]["utilities"]
                        for key, val in utils.items():
                            key = "_".join(key.split('_')[:-2])
                            if key in ul:
                                ul[key].append(val)
                            else:
                                ul[key] = [val]
                if "accept" in action:
                    utils = action["accept"]["utilities"]
                    had_acceptence = True
                    for key, val in utils.items():
                        key = "_".join(key.split('_')[:-2])
                        if show_all:
                            if key in ul:
                                ul[key].append(val)
                            else:
                                ul[key] = [val]
                        if key in aul:
                            aul[key].append(val)
                        else:
                            aul[key] = [val]
            if not had_acceptence:
                utils = neg_file_dict["SAOPState"]["actions"][0]["offer"]["utilities"]
                for key, val in utils.items():
                    key = "_".join(key.split('_')[:-2])
                    if key in aul:
                        aul[key].append(0)
                    else:
                        aul[key] = [0]
        if show_all:
            for key, val in ul.items():
                plt.plot(np.array(val), label=m.group())
            plt.legend(loc='upper left', borderaxespad=0.)
            plt.ylabel("Util")
            plt.title(f_p)
            plt.xlabel("Turn (last turn is accepted)")
            plt.show()
    a_aul = {}
    for key, val in aul.items():
        np_val = np.array(val)
        print("key: " + key + ", number of negotation results: " + str(len(np_val)) + "\n")
        print(np_val)
        print("\n")
        a_aul[key] = np.average(np_val)
        plt.plot(np_val, label=key)
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
    #ANL_Build_Docker_Settings()

# See PyCharm help at https://www.jetbrains.com/help/pycharm/
