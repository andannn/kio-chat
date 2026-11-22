--
-- PostgreSQL database dump
--

\restrict uMBaXMSA41h3rmTTKTE2OncM7AgYHrHVLau9JiRKxF0oeKcrv4NOGJeO7NgUrmP

-- Dumped from database version 17.10 (Debian 17.10-1.pgdg13+1)
-- Dumped by pg_dump version 18.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: create_user_room(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_user_room() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    new_room_id BIGINT;
BEGIN
    INSERT INTO rooms(name) VALUES (NEW.username || '的房间')
        RETURNING id INTO new_room_id;
    INSERT INTO room_members(room_id, user_id, role) VALUES (new_room_id, NEW.id, 'owner');
    RETURN NEW;
END;
$$;


--
-- Name: notify_chat_message(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_chat_message() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    message_row messages%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        message_row := OLD;
    ELSE
        message_row := NEW;
    END IF;

    PERFORM pg_notify('chat_messages', json_build_object(
        'operation', TG_OP,
        'message_id', message_row.id,
        'room_id', message_row.room_id
    )::TEXT);
    RETURN NULL;
END;
$$;


--
-- Name: notify_room(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_room() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM pg_notify('rooms', json_build_object(
        'operation', TG_OP,
        'room_id', NEW.id
    )::TEXT);
    RETURN NULL;
END;
$$;


--
-- Name: notify_room_join_request(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_room_join_request() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    request_row room_join_requests%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        request_row := OLD;
    ELSE
        request_row := NEW;
    END IF;

    PERFORM pg_notify('room_join_requests', json_build_object(
        'operation', TG_OP,
        'request_id', request_row.id,
        'request_room_id', request_row.room_id,
        'request_user_id', request_row.user_id
    )::TEXT);
    RETURN NULL;
END;
$$;


--
-- Name: notify_room_member(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_room_member() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    member_row room_members%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        member_row := OLD;
    ELSE
        member_row := NEW;
    END IF;

    PERFORM pg_notify('room_members', json_build_object(
        'operation', TG_OP,
        'room_id', member_row.room_id,
        'user_id', member_row.user_id
    )::TEXT);
    RETURN NULL;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.messages (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    text text NOT NULL,
    create_at timestamp with time zone DEFAULT now() NOT NULL,
    room_id bigint DEFAULT 1 NOT NULL
);


--
-- Name: messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.messages_id_seq OWNED BY public.messages.id;


--
-- Name: room_join_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_join_requests (
    id bigint NOT NULL,
    room_id bigint NOT NULL,
    user_id bigint NOT NULL,
    create_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: room_join_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.room_join_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: room_join_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.room_join_requests_id_seq OWNED BY public.room_join_requests.id;


--
-- Name: room_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_members (
    room_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role text DEFAULT 'member'::text NOT NULL,
    CONSTRAINT room_members_role_check CHECK ((role = ANY (ARRAY['owner'::text, 'member'::text])))
);


--
-- Name: rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rooms (
    id bigint NOT NULL,
    name text NOT NULL,
    create_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: rooms_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rooms_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rooms_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rooms_id_seq OWNED BY public.rooms.id;


--
-- Name: sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sessions (
    id text NOT NULL,
    user_id bigint NOT NULL,
    create_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    username text NOT NULL,
    password_hash text NOT NULL,
    create_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: messages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages ALTER COLUMN id SET DEFAULT nextval('public.messages_id_seq'::regclass);


--
-- Name: room_join_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_join_requests ALTER COLUMN id SET DEFAULT nextval('public.room_join_requests_id_seq'::regclass);


--
-- Name: rooms id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms ALTER COLUMN id SET DEFAULT nextval('public.rooms_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: room_join_requests room_join_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_join_requests
    ADD CONSTRAINT room_join_requests_pkey PRIMARY KEY (id);


--
-- Name: room_join_requests room_join_requests_room_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_join_requests
    ADD CONSTRAINT room_join_requests_room_id_user_id_key UNIQUE (room_id, user_id);


--
-- Name: room_members room_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_members
    ADD CONSTRAINT room_members_pkey PRIMARY KEY (room_id, user_id);


--
-- Name: rooms rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT rooms_pkey PRIMARY KEY (id);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: messages_room_id_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX messages_room_id_id_idx ON public.messages USING btree (room_id, id);


--
-- Name: room_members_user_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX room_members_user_id_idx ON public.room_members USING btree (user_id);


--
-- Name: messages messages_notify; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER messages_notify AFTER INSERT OR DELETE OR UPDATE ON public.messages FOR EACH ROW EXECUTE FUNCTION public.notify_chat_message();


--
-- Name: room_join_requests room_join_requests_notify; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER room_join_requests_notify AFTER INSERT OR DELETE ON public.room_join_requests FOR EACH ROW EXECUTE FUNCTION public.notify_room_join_request();


--
-- Name: room_members room_members_notify; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER room_members_notify AFTER INSERT OR DELETE ON public.room_members FOR EACH ROW EXECUTE FUNCTION public.notify_room_member();


--
-- Name: rooms rooms_notify; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER rooms_notify AFTER UPDATE OF name ON public.rooms FOR EACH ROW EXECUTE FUNCTION public.notify_room();


--
-- Name: users users_create_room; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER users_create_room AFTER INSERT ON public.users FOR EACH ROW EXECUTE FUNCTION public.create_user_room();


--
-- Name: messages messages_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: messages messages_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_join_requests room_join_requests_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_join_requests
    ADD CONSTRAINT room_join_requests_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: room_join_requests room_join_requests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_join_requests
    ADD CONSTRAINT room_join_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_members room_members_room_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_members
    ADD CONSTRAINT room_members_room_id_fkey FOREIGN KEY (room_id) REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: room_members room_members_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_members
    ADD CONSTRAINT room_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: sessions sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict uMBaXMSA41h3rmTTKTE2OncM7AgYHrHVLau9JiRKxF0oeKcrv4NOGJeO7NgUrmP

