import pygame
import numpy as np 
import math

BLACK_COLOR = (0, 0, 0)
GREEN_COLOR = (0, 255, 0)

# Các hệ số của hàm mục tiêu cải tiến đã giảm xuống
factor_goal = 0.1            
factor_progress = 5          
factor_exploration = 1       
threshold_explore = 50       
threshold_speed = 1.0        
factor_stagnation = 50       

def euclidean_distance(x1, y1, x2, y2):
    return np.sqrt((x1 - x2)**2 + (y1 - y2)**2)

def revised_cost_function(robot, start, goal, stagnation_counter):
    # 1. Chi phí khoảng cách đến đích
    d_goal = euclidean_distance(robot.x, robot.y, goal[0], goal[1])
    cost_goal = factor_goal * d_goal

    # 2. Thưởng tiến về mục tiêu
    d_prev = euclidean_distance(robot.previous_position[0], robot.previous_position[1], goal[0], goal[1])
    progress = max(0, d_prev - d_goal)
    reward_progress = - factor_progress * progress

    # 3. Thưởng/phạt khám phá dựa trên khoảng cách từ điểm bắt đầu:
    d_start = euclidean_distance(robot.x, robot.y, start[0], start[1])
    if d_start > threshold_explore:
        bonus_exploration = - factor_exploration * (d_start - threshold_explore)
    else:
        bonus_exploration = factor_exploration * (threshold_explore - d_start)

    # 4. Phạt do đứng yên:
    current_speed = euclidean_distance(robot.x, robot.y, robot.previous_position[0], robot.previous_position[1])
    if current_speed < threshold_speed:
        stagnation_counter += 1
    else:
        stagnation_counter = 0
    penalty_stagnation = stagnation_counter * factor_stagnation

    total_cost = cost_goal + reward_progress + bonus_exploration + penalty_stagnation
    return total_cost, stagnation_counter

class Envir:
    def __init__(self, dim):
        self.black = (0, 0, 0)
        self.white = (255, 255, 255)
        self.red   = (255, 0, 0)
        self.green = (0, 255, 0)
        self.blue  = (0, 0, 255)

        self.height = dim[0]
        self.width = dim[1]

        pygame.display.set_caption("Robot Simulation")
        self.map = pygame.display.set_mode((self.width, self.height))
        self.font = pygame.font.SysFont('arial', 30)
        self.text = self.font.render('default', True, self.black, self.white)
        self.textRect = self.text.get_rect()
        self.textRect.center = (dim[1] - 300, dim[0] - 50)
        self.trail_set = []
    
    def info1(self, iteration, bestfitness):
        text = f"Iteration: {np.round(iteration, 2)}, Bestfit: {np.round(bestfitness, 2)}"
        self.text = self.font.render(text, True, self.black, self.white)
        self.textRect.center = (self.width - 850, self.height - 50)
        self.map.blit(self.text, self.textRect)

    def robot_frame(self, pos, rotation):
        n = 50
        centerx, centery = pos
        x_axis = (centerx + n * math.cos(rotation), centery + n * math.sin(rotation))
        y_axis = (centerx + n * math.cos(rotation + math.pi/2), centery + n * math.sin(rotation + math.pi/2))
        pygame.draw.line(self.map, self.blue, pos, x_axis, 3)
        pygame.draw.line(self.map, self.green, pos, y_axis, 3)

    def robot_sensor(self, pos, points):
        for point in points:
            pygame.draw.line(self.map, (255, 0, 0), pos, point, 1)
            pygame.draw.circle(self.map, (255, 0, 0), point, 3)

class Robot:
    def __init__(self, startpos, Img, width):
        self.w = width
        self.x = startpos[0]
        self.y = startpos[1]
        self.theta = 0 

        self.vx = 0
        self.vy = 0
        self.theta_d = 0 

        self.v1 = 0
        self.v2 = 0
        self.v3 = 0

        self.sensor_data = [0] * 10
        self.points = []
        self.crash = False
        self.time = 0
        self.cost_function = 0
        self.previous_theta = 0
        self.previous_position = startpos

        self.img = pygame.image.load(Img)
        self.img = pygame.transform.scale(self.img, (30, 30))
        self.rotated = self.img
        self.rect = self.rotated.get_rect(center=(self.x, self.y))
    
    def draw(self, map):
        map.blit(self.rotated, self.rect)
    
    def move(self):     
        inv_R = np.array([[np.cos(self.theta), -np.sin(self.theta), 0], 
                          [np.sin(self.theta),  np.cos(self.theta), 0], 
                          [0, 0, 1]])
        l = 10
        J1 = np.array([[1/np.sqrt(3), 0, -1/np.sqrt(3)], 
                       [-1/3, 2/3, -1/3], 
                       [-1/(3*l), -1/(3*l), -1/(3*l)]])
        r = 3
        J2 = np.array([[r, 0, 0], 
                       [0, r, 0], 
                       [0, 0, r]])

        AAA = inv_R @ J1 @ J2 @ np.array([[self.v1], [self.v2], [self.v3]])
        self.vx = AAA[0, 0]
        self.vy = AAA[1, 0]
        self.theta_d = AAA[2, 0]
        
        self.x += self.vx * dt
        self.y += self.vy * dt
        self.theta += self.theta_d * dt
        
        self.rotated = pygame.transform.rotozoom(self.img, -math.degrees(self.theta), 1)
        self.rect = self.rotated.get_rect(center=(self.x, self.y))
    
    def update_sensor_data(self):
        angles = [self.theta + i * (2 * math.pi / 10) for i in range(10)]
        edge_points = []
        edge_distances = []
        width, height = track_copy.get_size()
        for angle in angles:
            distance = 0
            edge_x, edge_y = int(self.x), int(self.y)
            while True:
                edge_x = int(self.x + distance * math.cos(angle))
                edge_y = int(self.y + distance * math.sin(angle))
                if 0 <= edge_x < width and 0 <= edge_y < height:
                    if track_copy.get_at((edge_x, edge_y)) == BLACK_COLOR:
                        break
                else:
                    break
                distance += 1
            edge_points.append((edge_x, edge_y))
            edge_distances.append(distance)
        self.sensor_data = edge_distances
        self.points = edge_points
    
    def check_crash(self):
        edge_x, edge_y = int(self.x), int(self.y)
        width, height = track_copy.get_size()
        min_distance = min(self.sensor_data)
        if 0 <= edge_x < width and 0 <= edge_y < height:
            if track_copy.get_at((edge_x, edge_y)) == BLACK_COLOR:
                self.crash = True
                self.cost_function += 1e11
            elif track_copy.get_at((edge_x, edge_y)) == GREEN_COLOR:
                self.crash = True
                self.cost_function = 0
            elif min_distance < 20:
                self.cost_function += 1000 * (20 - min_distance)
        else:
            self.crash = True
            self.cost_function += 1e11
        if self.time >= 50:
            self.crash = True
            self.cost_function += 5000 * np.sqrt((self.x - goal[0])**2 + (self.y - goal[1])**2)

# Khởi tạo Pygame và các biến toàn cục
pygame.init()
pygame.display.set_mode((900, 900))
track = pygame.image.load(r'D:\tgmt\map3.png')
track = pygame.transform.scale(track, (900, 900))
track_copy = track.copy()

start = (450, 50)
goal = (452, 835)
dims = (900, 900)

running = True
dt = 0
lasttime = pygame.time.get_ticks()
environment = Envir(dims)
number = 60
Robots = []
for i in range(number):
    Robots.append(Robot(start, r'D:\tgmt\robot.png', 1))

pop_size = number 
min_max = [-10, 10]
# Cấu trúc NN ban đầu: 13 đầu vào -> W1: (13,10)=130, W2: (10,10)=100, V1: (10,3)=30, bias: 10+10+3 = 23 -> Tổng = 283
npar = 283

w  = np.random.uniform(0.1, 1.5)
c1 = np.random.uniform(0.1, 1.5)
c2 = np.random.uniform(0.1, 1.5)

Pbest_position = np.zeros((pop_size, npar))
Gbest_position = np.zeros((1, npar))
Pbest_fitness = np.ones(pop_size) * 1e11
Gbest_fitness = 1e11
max_interation = 500

fitness = np.zeros(max_interation)
robot_available = pop_size 

P = np.random.uniform(min_max[0], min_max[1], (pop_size, npar))
V = P * 0

previous_positions = np.zeros((900, 900))
obstacle_map = np.zeros((900, 900))

def af(x):
    return np.tanh(x)

def vidu(x, W1, W2, V1, bias1, bias2, bias3):
    net1 = W1.T @ x + bias1.reshape(-1, 1)
    y_h1 = af(net1)
    net2 = W2.T @ y_h1 + bias2.reshape(-1, 1)
    y_h2 = af(net2)
    output = V1.T @ y_h2 + bias3.reshape(-1, 1)
    return output

# ... [phần khai báo, định nghĩa lớp, hàm, và các biến toàn cục như trước] ...

interation = 0
global_sim_time = 0  # Thời gian toàn cục của mô phỏng
last_debug_print = 0  # Thời gian in lần cuối

while running and interation < max_interation:
    # Reset trạng thái cho mỗi thế hệ (generation)
    for idx, robot in enumerate(Robots):
        robot.x = start[0]
        robot.y = start[1]
        robot.theta = np.pi/2
        robot.update_sensor_data()
        robot.crash = False
        robot.cost_function = 0 
        robot.time = 0 
        robot.previous_position = (robot.x, robot.y)
    
    robot_available = pop_size
    while robot_available > 0:
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                running = False
        for idx, robot in enumerate(Robots):
            if not robot.crash:
                ex = goal[0] - robot.x
                ey = goal[1] - robot.y
                etheta = -np.pi/2 - robot.theta
                # Đầu vào NN có 13 chiều: [ex, ey, etheta] + 10 cảm biến
                nn_input = np.vstack((np.array([[ex], [ey], [etheta]]), 
                                      np.array(robot.sensor_data).reshape(-1, 1)))
 
                W1 = P[idx, :130].reshape(13, 10)
                W2 = P[idx, 130:230].reshape(10, 10)
                V1 = P[idx, 230:260].reshape(10, 3)
                b1 = P[idx, 260:270].reshape(10, 1)
                b2 = P[idx, 270:280].reshape(10, 1)
                b3 = P[idx, 280:283].reshape(3, 1)
                
                YY = vidu(nn_input, W1, W2, V1, b1, b2, b3)
                robot.v1 = max(-12, min(YY[0, 0], 12))
                robot.v2 = max(-12, min(YY[1, 0], 12))
                robot.v3 = max(-12, min(YY[2, 0], 12))
                robot.move()
                
                robot.previous_position = (robot.x, robot.y)
                cost_val, stagnation = revised_cost_function(robot, start, goal, 0)
                robot.cost_function += cost_val
                robot.time += dt
                robot.check_crash()
                if robot.crash:
                    robot_available -= 1
                
                robot.draw(environment.map)
                robot.update_sensor_data()
                environment.robot_frame((robot.x, robot.y), robot.theta)
                environment.robot_sensor((robot.x, robot.y), robot.points)
  
        dt = (pygame.time.get_ticks() - lasttime) / 1000
        lasttime = pygame.time.get_ticks()
        global_sim_time += dt
        pygame.display.update()
        environment.info1(interation, Gbest_fitness)
        environment.map.blit(track, (0, 0))
        
        # In thông tin debug cho Robot 0 mỗi giây (nếu cần)
        #if global_sim_time - last_debug_print >= 1:
        #    r0 = Robots[0]
        #    print(f"--- Global Time: {global_sim_time:.2f}s ---")
        #    print(f"Robot 0: x={r0.x:.2f}, y={r0.y:.2f}, theta={r0.theta:.2f}, vx={r0.vx:.2f}, vy={r0.vy:.2f}, cost={r0.cost_function:.2f}")
        #    print(f"Sensor Data: {r0.sensor_data}")
        #    last_debug_print = global_sim_time
    
    # PSO cập nhật: sau mỗi thế hệ, cập nhật Pbest và Gbest
    for idx, robot in enumerate(Robots):
        J = robot.cost_function
        if J < Pbest_fitness[idx]:
            Pbest_fitness[idx] = J
            Pbest_position[idx] = P[idx].copy()
        if J < Gbest_fitness:
            Gbest_fitness = J
            Gbest_position = P[idx].copy()
        fitness[interation] = Gbest_fitness

    V = w * V + c1 * np.random.rand() * (Pbest_position - P) + c2 * np.random.rand() * (Gbest_position - P)
    P = P + V
    
    # Tìm robot có cost thấp nhất (điểm hội tụ hiện tại)
    best_robot = min(Robots, key=lambda r: r.cost_function)
    print(f"Iteration {interation}: Convergence position (best robot) at x={best_robot.x:.2f}, y={best_robot.y:.2f}, cost={best_robot.cost_function:.2f}")
    
    print(f"Iteration: {interation}, Best cost: {Gbest_fitness}")
    interation += 1

print("\nGbest_position (NN parameters):", Gbest_position)
pygame.quit()

