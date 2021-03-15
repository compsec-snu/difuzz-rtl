import os

class tileSrcReader():
    def __init__(self, input_name):
        if not os.path.isfile(input_name):
            raise Exception('No file exists: {}'.format(input_name))

        name_file = open(input_name, 'r')

        self.name_map = {}

        while True:
            line = name_file.readline()
            if not line: break
            if line[0:2] != '  ':
                key = line[:-1]
                self.name_map[key] = []
                while True:
                    val_line = name_file.readline()
                    if not val_line: break
                    elif '  ' != val_line[0:2]: break

                    self.name_map[key].append(val_line[2:-1])

                if not val_line: break
                elif val_line != '\n':
                    raise Exception('Name file {} must contain new line between entries'.format(input_name))

        name_file.close()


    def return_map(self):
        return self.name_map
