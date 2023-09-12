#include "contiki.h"
#include "net/routing/routing.h"
#include "mqtt.h"
#include "os/sys/log.h"
#include "mqtt-client.h"
#include <stdlib.h>
#include <stdio.h>
#include "sys/node-id.h"
#include <string.h>
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "sys/etimer.h"

// Default config values
#define DEFAULT_BROKER_PORT 1883
#define DEFAULT_PUBLISH_INTERVAL (10 * CLOCK_SECOND)
static uint8_t mqtt_state;

#define LOG_MODULE "mqtt-client"
#ifdef MQTT_CLIENT_CONF_LOG_LEVEL
#define LOG_LEVEL MQTT_CLIENT_CONF_LOG_LEVEL
#else
#define LOG_LEVEL LOG_LEVEL_DBG
#endif

#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"

static const char *mqtt_broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;

#define STATE_INIT 0
#define STATE_NET_OK 1
#define STATE_CONNECTING 2
#define STATE_CONNECTED 3
#define STATE_SUBSCRIBED 4
#define STATE_DISCONNECTED 5

PROCESS_NAME(mqtt_humidity_process);
AUTOSTART_PROCESSES(&mqtt_humidity_process);
#define MAX_TCP_SEGMENT_SIZE 32
#define CONFIG_IP_ADDR_STR_LEN 64
#define BUFFER_SIZE 64

static char mqtt_publish_topic[BUFFER_SIZE];
static char mqtt_subscribe_topic[BUFFER_SIZE];
static char mqtt_client_id[BUFFER_SIZE];

static int humidity_percentage = 50;

// Timer to check the state of MQTT client
#define STATE_MACHINE_PERIODIC (CLOCK_SECOND >> 1)
static struct etimer mqtt_periodic_timer;
static struct etimer mqtt_publish_timer;
#define APP_BUFFER_SIZE 512
static char mqtt_app_buffer[APP_BUFFER_SIZE];

static struct mqtt_message *mqtt_msg_ptr = 0;

static struct mqtt_connection mqtt_conn;

PROCESS(mqtt_humidity_process, "MQTT Client");

static void
mqtt_publish_handler(const char *topic, uint16_t topic_len, const uint8_t *chunk, uint16_t chunk_len)
{

  printf("Publish Handler: topic='%s' %s\n", topic, (char *)chunk);
}

static void
mqtt_event_handler(struct mqtt_connection *m, mqtt_event_t event, void *data)
{
  switch (event)
  {
  case MQTT_EVENT_CONNECTED:
  {
    printf("MQTT Connection Established\n");

    mqtt_state = STATE_CONNECTED;
    break;
  }
  case MQTT_EVENT_DISCONNECTED:
  {
    printf("MQTT Disconnect. %u\n", *((mqtt_event_t *)data));

    mqtt_state = STATE_DISCONNECTED;
    process_poll(&mqtt_humidity_process);
    break;
  }
  case MQTT_EVENT_PUBLISH:
  {
    mqtt_msg_ptr = data;

    mqtt_publish_handler(mqtt_msg_ptr->topic, strlen(mqtt_msg_ptr->topic), mqtt_msg_ptr->payload_chunk, mqtt_msg_ptr->payload_length);
    break;
  }
  case MQTT_EVENT_SUBACK:
  {
#if MQTT_311
    mqtt_suback_event_t *suback_event = (mqtt_suback_event_t *)data;

    if (suback_event->success)
    {
      printf("Subscrib to topic status= \n");
    }
    else
    {
      printf("Subscrib to topic status= Failed (ret code %x)\n", suback_event->return_code);
    }
#else
    printf("Subscrib to topic status= successful\n");
#endif
    break;
  }
  case MQTT_EVENT_UNSUBACK:
  {
    printf("Unsubscrib to topic status= successful\n");
    break;
  }
  case MQTT_EVENT_PUBACK:
  {
    printf("Publishing complete.\n");
    break;
  }
  default:
    printf("Unhandled MQTT event: %i\n", event);
    break;
  }
}

static bool
mqtt_have_connectivity(void)
{
  if (uip_ds6_get_global(ADDR_PREFERRED) == NULL ||
      uip_ds6_defrt_choose() == NULL)
  {
    return false;
  }
  return true;
}

PROCESS_THREAD(mqtt_humidity_process, ev, data)
{

  PROCESS_BEGIN();

  mqtt_status_t mqtt_status;
  char mqtt_broker_address[CONFIG_IP_ADDR_STR_LEN];

  printf("MQTT Client Process\n");

  snprintf(mqtt_client_id, BUFFER_SIZE, "%02x%02x%02x%02x%02x%02x",
           linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
           linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
           linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

  // Registration on the broker
  mqtt_register(&mqtt_conn, &mqtt_humidity_process, mqtt_client_id, mqtt_event_handler,
                MAX_TCP_SEGMENT_SIZE);

  mqtt_state = STATE_INIT;

  etimer_set(&mqtt_periodic_timer, STATE_MACHINE_PERIODIC);

  while (1)
  {

    PROCESS_YIELD();

    if ((ev == PROCESS_EVENT_TIMER && data == &mqtt_periodic_timer) ||
        ev == PROCESS_EVENT_POLL)
    {

      if (mqtt_state == STATE_INIT)
      {
        srand(2711986226);
        if (mqtt_have_connectivity() == true)
          mqtt_state = STATE_NET_OK;
      }
      if (mqtt_state == STATE_NET_OK)
      {

        printf("Connecting to MQTT server!\n");

        memcpy(mqtt_broker_address, mqtt_broker_ip, strlen(mqtt_broker_ip));
        mqtt_connect(&mqtt_conn, mqtt_broker_address, DEFAULT_BROKER_PORT,
                     (DEFAULT_PUBLISH_INTERVAL * 3) / CLOCK_SECOND,
                     MQTT_CLEAN_SESSION_ON);
        mqtt_state = STATE_CONNECTING;
      }

      if (mqtt_state == STATE_CONNECTED)
      {
        // Subscribe to a topic
        strcpy(mqtt_subscribe_topic, "humidity");

        mqtt_status = mqtt_subscribe(&mqtt_conn, NULL, mqtt_subscribe_topic, MQTT_QOS_LEVEL_0);

        printf("Subscribing!\n");

        if (mqtt_status == MQTT_STATUS_OUT_QUEUE_FULL)
        {
          LOG_ERR("Tried to subscribe to Humidity but command queue was full!\n");
          PROCESS_EXIT();
        }

        mqtt_state = STATE_SUBSCRIBED;
      }

      if (mqtt_state == STATE_SUBSCRIBED)
      {

        etimer_set(&mqtt_publish_timer, DEFAULT_PUBLISH_INTERVAL);
        PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&mqtt_publish_timer));

        printf(mqtt_publish_topic, "%s", "humidity");

        // Random humidity values
        humidity_percentage = (rand() % 40);

        snprintf(mqtt_app_buffer, APP_BUFFER_SIZE, "{\"HUM\":%d}", humidity_percentage);

        printf("%s \n", mqtt_app_buffer);

        mqtt_publish(&mqtt_conn, NULL, mqtt_publish_topic, (uint8_t *)mqtt_app_buffer, strlen(mqtt_app_buffer), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
      }
      else if (mqtt_state == STATE_DISCONNECTED)
      {
        LOG_ERR("Disconnected from MQTT broker\n");
      }

      etimer_reset(&mqtt_periodic_timer);
    }
  }
  PROCESS_END();
}
