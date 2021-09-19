#include <Wire.h>
#include <Kalman.h>

/*TODO:
   balence when pushed
*/

#define MPU6050_ADRESS 0X68
#define POWER_CONFIG_ADDRESS 0x6B
#define GYRO_CONFIG_ADDRESS 0x1B
#define ACCELEROMETER_CONFIG_ADDRESS 0x1C
#define MOTOR1_EN_PIN  8
#define MOTOR2_EN_PIN  12
#define MOTOR2_F_PIN  11
#define MOTOR2_B_PIN  10
#define MOTOR1_F_PIN  3
#define MOTOR1_B_PIN  9
const float TO_DEGREES = 180 / PI ;

Kalman kalman;
long acc_x = 0, acc_y = 0, acc_z = 0;
long acc_total_vector = 0;
float acc_pitch, acc_roll, acc_yaw;
long gyro_x, gyro_y, gyro_z;
float x_rate, y_rate, z_rate;
float gyro_roll_angle, gyro_pitch_angle, gyro_yaw_angle;
float pitch_angle_comp_filtered, roll_angle_comp_filtered, pitch_angle_kalm_filtered, roll_angle_kalm_filtered;
long loopTime ;
float dt = 0.003;
int tempReading;
float offset_x = 0, offset_y = 0, offset_z = 0;
bool initalAngleCorected = false;
long loop_time = 0;

float kp = 0.0;
float ki = 0.0;
float kd = 0.0;
float angle = 0.0;
float deadGap = 0.0;
float pError = 0.0;
float error = 0.0;
float ref0 = 0;
float ref = 0;
float P, I, D ;
float steerRight = 0.0 , steerLeft = 0.0;
float forward = 0.0 , backward = 0.0;
bool start = false ;
int period = 4000;
int period0 = 4000;
int Vmax = 165;
int Imax = 50;
bool allSetUp = false;
float rightSpeed = 0, leftSpeed = 0;
bool printAngle = false;

void setup() {

  Wire.begin(); // Initiate the Wire library
  TWBR = 12;  //Set the I2C clock speed to 400kHz
  Serial.begin(9600);

  //use the builtin led to indicate calibratin
  pinMode(13, OUTPUT);

  //setup motors and H-bridge
  pinMode(MOTOR1_EN_PIN, OUTPUT);
  pinMode(MOTOR2_EN_PIN, OUTPUT);
  pinMode(MOTOR2_F_PIN, OUTPUT);
  pinMode(MOTOR2_B_PIN, OUTPUT);
  pinMode(MOTOR1_F_PIN, OUTPUT);
  pinMode(MOTOR1_B_PIN, OUTPUT);

  //wait for handchake !!
  while (true) {
    Serial.println("h");
    delay(500);
    if (Serial.available() && Serial.read() == 'i') break;

  }


  //receive initial settings and parameters
  Serial.println("setmeup");
  int sCounter = 0;
  while (!allSetUp) {
    if (Serial.available()) {
      switch (sCounter) {
        case 0:
          ref0 = ((float)Serial.parseInt()) * 0.01;
          ref = ref0;
          break;
        case 1:
          deadGap = ((float)Serial.parseInt()) * 0.01;
          break;
        case 2:
          Vmax = Serial.parseInt();
          break;
        case 3:
          Imax = Serial.parseInt();
          break;
        case 4:
          period0 = Serial.parseInt();
          period = period0;
          break;
        case 5:
          kp = (float) Serial.parseInt();
          break;
        case 6:
          ki = (float) Serial.parseInt();
          break;
        case 7:
          kd = (float) Serial.parseInt();
          allSetUp = true;
          break;
      }
      sCounter++;
      clearSerialBuffer();
    }
  }

  allSetUp = true;

  
  //enable motors:
  digitalWrite(MOTOR1_EN_PIN, 1);
  digitalWrite(MOTOR2_EN_PIN, 1);

  // setup mpu and calibrate gyro
  setup_mpu_6050_registers();
  calibrate(2000); // calibrate the gyroscope

  //setup kalman parameters (Optional)
  //kalman.setR_meas(0.0001);
  //kalman.setQ_angle(0.0001);
  //kalman.setQ_bias(9);

  //loopTime = micros();
  loop_time = micros() + period;

}
void loop() {

  if (Serial.available()) {
    char control = (char)Serial.peek();
    switch (control) {
      case 'R'://steer right
        steerRight = Serial.parseInt() ;
        steerRight = map(steerRight, 0, 255, 0, 30);
        clearSerialBuffer();
        break;
      case 'L'://steer left
        steerLeft = Serial.parseInt() ;
        steerLeft = map(steerLeft, 0, 255, 0, 30);
        clearSerialBuffer();
        break;
      case 'F'://move forward
        forward = map(Serial.parseInt(), 0, 255, 0, 2);
        ref = ref0 + forward;
        clearSerialBuffer();
        break;
      case 'B'://move backward
        backward = map(Serial.parseInt(), 0 , 255, 0, 2);
        ref = ref0 - backward;
        clearSerialBuffer();
        break;

    }

    rightSpeed = - steerRight  + steerLeft ;
    leftSpeed  = steerRight  - steerLeft ;
  }



  if (start) {
    calculateAnglesAndRates();

    angle = roll_angle_kalm_filtered ;
    //angle = (angle < 0.5  && angle  >= 0)  ? angle-=5*(angle/abs(angle)) : angle ;
    error = ref - angle;

    if (abs(angle) > 40) {
      digitalWrite(MOTOR1_EN_PIN, 0);
      digitalWrite(MOTOR2_EN_PIN, 0);
      I = 0;
    }
    else {
      digitalWrite(MOTOR1_EN_PIN, 1);
      digitalWrite(MOTOR2_EN_PIN, 1);
    }

    // Proportional Term
    P = kp * error ;

    // Integral Term
    I += ki * error;

    //integral windup
    if (abs(I) > Imax) I = I > 0 ? Imax : -Imax;

    // Derevative Term
    D = kd * (error - pError);
    pError = error;

    float pid = abs(angle) < deadGap ? 0 : (P + I + D) ; // dead band

    pid  = constrain(pid, -Vmax, Vmax);

    //The self balancing point is adjusted when there is not forward or backwards movement from the app. This way the robot will always find it's balancing point
    //    if (forward == 0.0 && backward == 0.0) {                                                  //If the setpoint is zero degrees
    //      if (pid < 0)ref += 0.0001;                 //Increase the self_balance_pid_setpoint if the robot is still moving forewards
    //      if (pid > 0)ref -= 0.0001;                 //Decrease the self_balance_pid_setpoint if the robot is still moving backwards
    //    }


    move(pid);

  }
  else {
    digitalWrite(MOTOR1_EN_PIN, 0);
    digitalWrite(MOTOR2_EN_PIN, 0);
    P = 0;
    I = 0;
    D = 0;
  }

  //

  if (printAngle) {
    calculateAnglesAndRates();
    Serial.println(roll_angle_kalm_filtered);
  }
  //Serial.println(micros() - loop_time);
  while (micros() < loop_time);
  loop_time = micros() + period;
}



void move(float speed) {

  float turn = 0.0;

  if (speed > 0) {                        //turn motors in + side

    digitalWrite(MOTOR1_B_PIN, 0);
    digitalWrite(MOTOR2_B_PIN, 0);
    analogWrite(MOTOR1_F_PIN, speed + rightSpeed);
    analogWrite(MOTOR2_F_PIN, speed + leftSpeed);

  }
  else {                                 //turn motors in - side

    digitalWrite(MOTOR1_F_PIN, 0);
    digitalWrite(MOTOR2_F_PIN, 0);
    analogWrite(MOTOR1_B_PIN, -speed - rightSpeed);
    analogWrite(MOTOR2_B_PIN, -speed - leftSpeed);

  }
}

void calibrate(int loops) {
  long x_cal = 0, y_cal = 0, z_cal = 0 ;
  Serial.println("Calibrating ...");
  for (int i = 0; i < loops; i++) {

    Wire.beginTransmission(MPU6050_ADRESS);
    Wire.write(0x43);
    Wire.endTransmission();
    Wire.requestFrom(MPU6050_ADRESS, 6);
    while (Wire.available() < 6);
    x_cal += Wire.read() << 8 | Wire.read();
    y_cal += Wire.read() << 8 | Wire.read();
    z_cal += Wire.read() << 8 | Wire.read();
    if (i % 100 == 0) digitalWrite(13, !digitalRead(13));
    delayMicroseconds(3700);// simulate main loop frequency
  }

  offset_x = x_cal / loops ;
  offset_y = y_cal / loops ;
  offset_z = z_cal / loops ;

  Serial.println(offset_x);
  Serial.println(offset_y);
  Serial.println(offset_z);
  Serial.println("gyro calibrated");

}


//void calibrate(int loops) {
//  long x_cal = 0, y_cal = 0, z_cal = 0 ;
//  Serial.print("Calibrating ...");
//  for (int i = 0; i < loops; i++) {
//    getDataFormMPU();
//    x_cal += gyro_x;
//    y_cal += gyro_y;
//    z_cal += gyro_z;
//    if (i % 100 == 0) Serial.print(".");
//  }
//
//  offset_x = x_cal / loops ;
//  offset_y = y_cal / loops ;
//  offset_z = z_cal / loops ;
//
//  Serial.println(offset_x);
//  Serial.println(offset_y);
//  Serial.println(offset_z);
//
//}

void getDataFormMPU() {

  Wire.beginTransmission(MPU6050_ADRESS);                              //Start communicating with the MPU-6050
  Wire.write(0X3B);                                                    //Set the requested starting address
  Wire.endTransmission();                                              //end transmission
  Wire.requestFrom(MPU6050_ADRESS, 14);                                 //request for the spesified registers
  while (Wire.available() < 14);                                       //Wait until all the bytes are received
  acc_x       = Wire.read() << 8 | Wire.read();                        //Add the low and high byte to the acc_x variable
  acc_y       = Wire.read() << 8 | Wire.read();
  acc_z       = Wire.read() << 8 | Wire.read();
  tempReading = Wire.read() << 8 | Wire.read();
  gyro_x      = Wire.read() << 8 | Wire.read();
  gyro_y      = Wire.read() << 8 | Wire.read();
  gyro_z      = Wire.read() << 8 | Wire.read();

  //dt =  ((float)(micros() - loopTime)) / 1000000;
  //loopTime = micros();
}


void calculateAnglesAndRates() {

  //Read the raw acc and gyro data from the MPU-6050
  getDataFormMPU();


  //calculate angles from Accelerometer measuements
  acc_total_vector = sqrt((acc_x * acc_x) + (acc_y * acc_y) + (acc_z * acc_z)); //Calculate the total accelerometer vector
  if (acc_total_vector != 0) {
    acc_pitch = asin((float)acc_y / acc_total_vector) * TO_DEGREES;  //Calculate the pitch angle
    acc_roll  = asin((float)acc_x / acc_total_vector) * -TO_DEGREES; //Calculate the roll angle
  }
  else {
    acc_pitch = acc_roll = 0;
  }


  //subtract the calibration offset
  gyro_x -= offset_x;
  gyro_y -= offset_y;
  gyro_z -= offset_z;

  //calculate Gyro Rate
  x_rate =  gyro_x / 65.5;
  y_rate =  gyro_y / 65.5;
  z_rate =  gyro_z / 65.5;

  //calculate Gyro angle

  if (!initalAngleCorected) {
    gyro_pitch_angle = acc_pitch;
    gyro_roll_angle = acc_roll;
    initalAngleCorected = true ;
    kalman.setAngle(acc_roll);

  } else {
    gyro_pitch_angle +=  gyro_x * dt / 65.5;
    gyro_roll_angle +=  gyro_y * dt / 65.5;
  }



  //=============================== Complementary filter ============================================

  //pitch_angle_comp_filtered = gyro_pitch_angle * 0.9 + acc_pitch * 0.1;   //Take 90% of the output pitch value and add 10% of the raw pitch value
  //roll_angle_comp_filtered = gyro_roll_angle * 0.96 + acc_roll * 0.04;      //Take 96% of the output roll value and add 4% of the raw roll value

  //================================== Kalman filter ============================================

  roll_angle_kalm_filtered = kalman.getAngle(y_rate, acc_roll, dt);

}

void setup_mpu_6050_registers() {

  //Activate the MPU-6050
  Wire.beginTransmission(MPU6050_ADRESS);                              //Start communicating with the MPU-6050
  Wire.write(POWER_CONFIG_ADDRESS);                                    //Send the requested starting register
  Wire.write(0x00);                                                    //Set the requested starting register
  Wire.endTransmission();                                              //End the transmission
  //Configure the accelerometer (+/-4g)
  Wire.beginTransmission(MPU6050_ADRESS);                              //Start communicating with the MPU-6050
  Wire.write(ACCELEROMETER_CONFIG_ADDRESS);                            //Send the requested starting register
  Wire.write(0x08);                                                    //Set the requested starting register
  Wire.endTransmission();                                              //End the transmission
  //Configure the gyro (250°/s full scale)
  Wire.beginTransmission(MPU6050_ADRESS);                              //Start communicating with the MPU-6050
  Wire.write(GYRO_CONFIG_ADDRESS);                                     //Send the requested starting register
  Wire.write(0x00);                                                    //Set the requested starting register
  Wire.endTransmission();                                              //End the transmission
  //Configure the programmable Low Pass Filter
  Wire.beginTransmission(MPU6050_ADRESS);                              //Start communicating with the MPU-6050
  Wire.write(0x1A);                                                    //Send the requested starting register
  Wire.write(0x03);                                                    //Set the requested starting register
  Wire.endTransmission();                                              //End the transmission
}


void clearSerialBuffer() {
  while (Serial.available()) Serial.read();
}



void calculateReferanceAngle() {

  double ref_ang = 0;
  for (int i = 0; i < 100; i++) {
    calculateAnglesAndRates();
    ref_ang += roll_angle_kalm_filtered;
  }
  ref0 = (float)(ref_ang / 100);
  ref = ref0;
  String s = "";
  s.concat(ref0);
  Serial.println(s);

}

void serialEvent() {
  if (Serial.available() && allSetUp) {

    char control = (char)Serial.peek();
    switch (control) {
      case 'P':
        kp = Serial.parseInt() ;
        break;
      case 'I':
        ki = Serial.parseInt() ;
        break;
      case 'D':
        kd = Serial.parseInt() ;
        break;
      case 'X':
        printAngle = true;
        period = 7000;
        clearSerialBuffer();
        break;
      case 'Y':
        printAngle = false;
        period = period0;
        clearSerialBuffer();
        break;
      case 'S'://steer left
        start = !start ;
        clearSerialBuffer();
        break;

      case 'C'://get the true referance angle
        calculateReferanceAngle();
        clearSerialBuffer();
        break;
    }

    //clearSerialBuffer();
  }

}
